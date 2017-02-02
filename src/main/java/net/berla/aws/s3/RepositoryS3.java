package net.berla.aws.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.util.Base64;
import com.amazonaws.util.IOUtils;
import com.amazonaws.util.StringUtils;
import net.berla.aws.Status;
import net.berla.aws.git.Branch;
import net.berla.aws.git.SyncableRepository;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.eclipse.jgit.lib.FileMode.TYPE_FILE;
import static org.eclipse.jgit.lib.FileMode.TYPE_MASK;

/**
 * Wrapper for an S3-Git-Repository. Handles working tree files synchronization.
 *
 * @author Matthias Berla (matthias@berla.net)
 * @version $Revision$ $Date$
 */
public class RepositoryS3 implements SyncableRepository {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryS3.class);
    private static final Detector TIKA_DETECTOR = TikaConfig.getDefaultConfig().getDetector();

    private final AmazonS3 s3;
    private final Bucket bucket;
    private final Repository repository;
    private final URIish uri;
    private final Branch branch;

    public RepositoryS3(Bucket bucket, Repository repository, AmazonS3 s3, Branch branch) {
        this.s3 = s3;
        this.bucket = bucket;
        this.repository = repository;
        this.branch = branch;
        this.uri = new URIish().setScheme("amazon-s3").setHost(bucket.getName()).setPath(Constants.DOT_GIT);
    }

    @Override
    public Status call() throws Exception {
        // Get S3 file list
        final List<S3ObjectSummary> files = getS3ObjectSummaries();
        final Iterator<S3ObjectSummary> iter = files.iterator();

        try (final TreeWalk walker = new TreeWalk(repository)) {
            walker.addTree(getRevTree());
            walker.setRecursive(false);

            // Walk all files
            while (walker.next()) {
                // Enter directories
                if (walker.isSubtree()) {
                    walker.enterSubtree();
                    continue;
                }
                // Only accept file types (no symlinks, no gitlinks) as they cannot be created in S3
                if ((walker.getFileMode().getBits() & TYPE_MASK) != TYPE_FILE) {
                    continue;
                }
                // Here we have a real file!
                if (walk(iter, walker.getObjectId(0), walker.getPathString())) {
                    LOG.info("Uploaded file: {}", walker.getPathString());
                }
            }
        }

        // Delete remaining objects, as they are not in the repo anymore
        for (S3ObjectSummary file : files) {
            LOG.info("Deleting file: {}", file.getKey());
            s3.deleteObject(file.getBucketName(), file.getKey());
        }
        return Status.SUCCESS;
    }

    private List<S3ObjectSummary> getS3ObjectSummaries() {
        // Do not include .git repository
        // matches: .git, .git/test...
        final Pattern excludePattern = Pattern.compile(String.format("^(\\.ssh|%s)(\\/.+)*$", Pattern.quote(Constants.DOT_GIT)));

        // S3 limits the size of the response to 1000 entries. Batch the requests.
        ObjectListing listing = s3.listObjects(bucket.getName());
        List<S3ObjectSummary> summaries = listing.getObjectSummaries();
        while (listing.isTruncated()) {
            listing = s3.listNextBatchOfObjects(listing);
            summaries.addAll(listing.getObjectSummaries());
        }

        return summaries.stream().filter(file -> !excludePattern.matcher(file.getKey()).matches()).collect(Collectors.toList());
    }

    private RevTree getRevTree() throws IOException {
        Ref ref = repository.exactRef(branch.getFullRef());
        RevCommit commit = new RevWalk(repository).parseCommit(ref.getObjectId());
        return commit.getTree();
    }

    private boolean walk(Iterator<S3ObjectSummary> iter, ObjectId file, String path) throws IOException {
        byte[] content;
        byte[] newHash;
        LOG.debug("Start processing file: {}", path);
        try (DigestInputStream is = new DigestInputStream(repository.open(file).openStream(), DigestUtils.getMd5Digest())) {
            // Get content
            content = IOUtils.toByteArray(is);
            // Get hash
            newHash = is.getMessageDigest().digest();
        }
        if (isUploadFile(iter, path, Hex.encodeHexString(newHash))) {
            LOG.info("Uploading file: {}", path);
            ObjectMetadata bucketMetadata = new ObjectMetadata();
            bucketMetadata.setContentMD5(Base64.encodeAsString(newHash));
            bucketMetadata.setContentLength(content.length);
            // Give Tika a few hints for the content detection
            Metadata tikaMetadata = new Metadata();
            tikaMetadata.set(Metadata.RESOURCE_NAME_KEY, FilenameUtils.getName(FilenameUtils.normalize(path)));
            // Fire!
            try (InputStream bis = TikaInputStream.get(content, tikaMetadata)) {
                bucketMetadata.setContentType(TIKA_DETECTOR.detect(bis, tikaMetadata).toString());
                s3.putObject(bucket.getName(), path, bis, bucketMetadata);
                return true;
            }
        }
        LOG.info("Skipping file (same checksum): {}", path);
        return false;
    }

    private boolean isUploadFile(Iterator<S3ObjectSummary> iter, String path, String hash) {
        while (iter.hasNext()) {
            S3ObjectSummary fileS3 = iter.next();
            // Filename should look like this:
            // a/b
            if (!fileS3.getKey().equals(path)) {
                // If this is another file, then continue!
                continue;
            }
            // Remove the file from the S3 list as it does not need to be processed further
            iter.remove();
            // Upload if the hashes differ
            return StringUtils.isNullOrEmpty(hash) || !fileS3.getETag().equals(hash);
        }
        return true;
    }

    @Override
    public URIish getUri() {
        return uri;
    }
}
