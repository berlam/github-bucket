package net.berla.aws.git;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;

import java.io.IOException;
import java.io.InputStream;

/**
 * Load the Deploy-Key for the Github-Repo.
 *
 * @author Matthias Berla (matthias@berla.net)
 * @version $Revision$ $Date$
 */
public class SecureShellAuthentication implements TransportConfigCallback {

    private final JschConfigSessionFactory factory;

    public SecureShellAuthentication(Bucket bucket, AmazonS3 client) {
        factory = new JschConfigSessionFactory() {

            @Override
            public synchronized RemoteSession getSession(URIish uri, CredentialsProvider credentialsProvider, FS fs, int tms) throws TransportException {
                // Do not check for default ssh user config
                fs.setUserHome(null);
                return super.getSession(uri, credentialsProvider, fs, tms);
            }

            @Override
            protected void configure(OpenSshConfig.Host host, Session session) {
                session.setConfig("HashKnownHosts", "no");
                if ("localhost".equalsIgnoreCase(host.getHostName())) {
                    session.setConfig("StrictHostKeyChecking", "no");
                }
            }

            @Override
            protected void configureJSch(JSch jsch) {
                S3Object file;
                file = client.getObject(bucket.getName(), ".ssh/known_hosts");
                try (InputStream is = file.getObjectContent()) {
                    jsch.setKnownHosts(is);
                } catch (IOException | JSchException e) {
                    throw new IllegalArgumentException("Missing known hosts file on s3: .ssh/known_hosts", e);
                }
                file = client.getObject(bucket.getName(), ".ssh/id_rsa");
                try (InputStream is = file.getObjectContent()) {
                    jsch.addIdentity("git", IOUtils.toByteArray(is), null, new byte[0]);
                } catch (IOException | JSchException e) {
                    throw new IllegalArgumentException("Missing key file on s3: .ssh/id_rsa", e);
                }
            }
        };
    }

    @Override
    public void configure(Transport transport) {
        if (transport instanceof SshTransport) {
            ((SshTransport) transport).setSshSessionFactory(factory);
        }
    }
}
