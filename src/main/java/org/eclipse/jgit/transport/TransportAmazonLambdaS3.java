package org.eclipse.jgit.transport;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.Md5Utils;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.Ref.Storage;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;

import static org.eclipse.jgit.transport.TransportAmazonS3.S3_SCHEME;

/**
 * A jgit transport intended to be used with AWS Lambda.
 *
 * @author Matthias Berla (matthias@berla.net)
 * @version $Revision$ $Date$
 */
public class TransportAmazonLambdaS3 extends Transport implements WalkTransport {

    private final AmazonS3 s3;
    private final String bucket;
    private final String keyPrefix;

    private TransportAmazonLambdaS3(Repository local, URIish uri, AmazonS3 client) {
        super(local, uri);
        bucket = uri.getHost();

        String p = uri.getPath();
        if (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        this.keyPrefix = p;
        this.s3 = client;
    }

    public FetchConnection openFetch() throws TransportException {
        final DatabaseS3 c = new DatabaseS3(bucket, keyPrefix + "/objects");
        final WalkFetchConnection r = new WalkFetchConnection(this, c);
        r.available(c.readAdvertisedRefs());
        return r;
    }

    public PushConnection openPush() throws TransportException {
        final DatabaseS3 c = new DatabaseS3(bucket, keyPrefix + "/objects");
        final WalkPushConnection r = new WalkPushConnection(this, c);
        r.available(c.readAdvertisedRefs());
        return r;
    }

    @Override
    public void close() {
        // No explicit connections are maintained.
    }

    public static final class TransportProtocolS3 extends TransportProtocol {

        private final AmazonS3 client;

        public TransportProtocolS3(AmazonS3 client) {
            this.client = client;
        }

        public String getName() {
            return "Amazon S3 from Lambda";
        }

        public Set<String> getSchemes() {
            return Collections.singleton(S3_SCHEME);
        }

        public Set<URIishField> getRequiredFields() {
            return Collections.unmodifiableSet(EnumSet.of(URIishField.HOST, URIishField.PATH));
        }

        public Transport open(URIish uri, Repository local, String remoteName) throws NotSupportedException {
            return new TransportAmazonLambdaS3(local, uri, client);
        }
    }

    @SuppressWarnings("Duplicates")
    class DatabaseS3 extends WalkRemoteObjectDatabase {

        private final String bucketName;

        private final String objectsKey;

        DatabaseS3(final String b, final String o) {
            bucketName = b;
            objectsKey = o;
        }

        private String resolveKey(String subpath) {
            if (subpath.endsWith("/")) //$NON-NLS-1$
                subpath = subpath.substring(0, subpath.length() - 1);
            String k = objectsKey;
            while (subpath.startsWith(ROOT_DIR)) {
                k = k.substring(0, k.lastIndexOf('/'));
                subpath = subpath.substring(3);
            }
            return k + "/" + subpath; //$NON-NLS-1$
        }

        @Override
        URIish getURI() {
            URIish u = new URIish();
            u = u.setScheme(S3_SCHEME);
            u = u.setHost(bucketName);
            u = u.setPath("/" + objectsKey); //$NON-NLS-1$
            return u;
        }

        @Override
        Collection<WalkRemoteObjectDatabase> getAlternates() throws IOException {
            try {
                return readAlternates(INFO_ALTERNATES);
            }
            catch (FileNotFoundException err) {
                // Fall through.
            }
            return null;
        }

        @Override
        WalkRemoteObjectDatabase openAlternate(final String location) throws IOException {
            return new DatabaseS3(bucketName, resolveKey(location));
        }

        @Override
        Collection<String> getPackNames() throws IOException {
            final HashSet<String> have = new HashSet<>();
            String prefix = resolveKey("pack");
            List<S3ObjectSummary> pack = getS3ObjectSummaries(prefix);

            for (S3ObjectSummary s3ObjectSummary : pack) {
                have.add(s3ObjectSummary.getKey().substring(prefix.length() + 1));
            }

            final Collection<String> packs = new ArrayList<>();
            for (final String n : have) {
                if (!n.startsWith("pack-") || !n.endsWith(".pack")) //$NON-NLS-1$ //$NON-NLS-2$
                    continue;

                final String in = n.substring(0, n.length() - 5) + ".idx"; //$NON-NLS-1$
                if (have.contains(in))
                    packs.add(n);
            }
            return packs;
        }

        private List<S3ObjectSummary> getS3ObjectSummaries(String prefix) {
            // S3 limits the size of the response to 1000 entries. Batch the requests.
            ObjectListing listing = s3.listObjects(bucket, prefix);
            List<S3ObjectSummary> summaries = listing.getObjectSummaries();
            while (listing.isTruncated()) {
                listing = s3.listNextBatchOfObjects(listing);
                summaries.addAll(listing.getObjectSummaries());
            }
            return summaries;
        }

        @Override
        FileStream open(final String path) throws IOException {
            String resolveKey = resolveKey(path);
            if (!s3.doesObjectExist(bucketName, resolveKey)) {
                // Throwing a FileNotFoundException is the
                // default behaviour of other walking services
                throw new FileNotFoundException(resolveKey);
            }
            S3Object c = s3.getObject(bucket, resolveKey);
            S3ObjectInputStream raw = c.getObjectContent();
            long len = c.getObjectMetadata().getContentLength();
            return new FileStream(raw, len);
        }

        @Override
        void deleteFile(final String path) throws IOException {
            s3.deleteObject(bucket, resolveKey(path));
        }

        @Override
        OutputStream writeFile(final String path, final ProgressMonitor monitor, final String monitorTask) throws IOException {
            return new ByteArrayOutputStream() {

                @Override
                public void close() throws IOException {
                    writeFile(path, toByteArray());
                }
            };
        }

        @Override
        void writeFile(final String path, final byte[] data) throws IOException {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentMD5(Md5Utils.md5AsBase64(data));
            metadata.setContentLength(data.length);
            try (InputStream input = new ByteArrayInputStream(data)) {
                s3.putObject(bucket, resolveKey(path), input, metadata);
            }
        }

        Map<String, Ref> readAdvertisedRefs() throws TransportException {
            final TreeMap<String, Ref> avail = new TreeMap<>();
            readPackedRefs(avail);
            readLooseRefs(avail);
            readRef(avail, Constants.HEAD);
            return avail;
        }

        private void readLooseRefs(final TreeMap<String, Ref> avail) throws TransportException {
            try {
                // S3 limits the size of the response to 1000 entries. Batch the requests.
                String prefix = resolveKey(ROOT_DIR + "refs");
                List<S3ObjectSummary> refs = getS3ObjectSummaries(prefix);
                for (final S3ObjectSummary ref : refs) {
                    readRef(avail, "refs/" + ref.getKey().substring(prefix.length() + 1));
                }
            }
            catch (IOException e) {
                throw new TransportException(getURI(), JGitText.get().cannotListRefs, e);
            }
        }

        private Ref readRef(final TreeMap<String, Ref> avail, final String rn) throws TransportException {
            final String s;
            String ref = ROOT_DIR + rn;
            try {
                try (BufferedReader br = openReader(ref)) {
                    s = br.readLine();
                }
            }
            catch (FileNotFoundException noRef) {
                return null;
            }
            catch (IOException err) {
                throw new TransportException(getURI(), MessageFormat.format(JGitText.get().transportExceptionReadRef, ref), err);
            }

            if (s == null)
                throw new TransportException(getURI(), MessageFormat.format(JGitText.get().transportExceptionEmptyRef, rn));

            if (s.startsWith("ref: ")) { //$NON-NLS-1$
                final String target = s.substring("ref: ".length()); //$NON-NLS-1$
                Ref r = avail.get(target);
                if (r == null)
                    r = readRef(avail, target);
                if (r == null)
                    r = new ObjectIdRef.Unpeeled(Storage.NEW, target, null);
                r = new SymbolicRef(rn, r);
                avail.put(r.getName(), r);
                return r;
            }

            if (ObjectId.isId(s)) {
                final Ref r = new ObjectIdRef.Unpeeled(loose(avail.get(rn)), rn, ObjectId.fromString(s));
                avail.put(r.getName(), r);
                return r;
            }

            throw new TransportException(getURI(), MessageFormat.format(JGitText.get().transportExceptionBadRef, rn, s));
        }

        private Storage loose(final Ref r) {
            if (r != null && r.getStorage() == Storage.PACKED)
                return Storage.LOOSE_PACKED;
            return Storage.LOOSE;
        }

        @Override
        void close() {
            // We do not maintain persistent connections.
        }
    }
}
