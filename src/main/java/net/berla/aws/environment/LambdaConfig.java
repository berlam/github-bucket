package net.berla.aws.environment;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.util.StringUtils;
import net.berla.aws.Config;
import net.berla.aws.git.Branch;
import net.berla.aws.git.Remote;
import net.berla.aws.git.SecureShellAuthentication;
import net.berla.aws.git.SyncableRepository;
import net.berla.aws.s3.RepositoryS3;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportAmazonLambdaS3;
import org.eclipse.jgit.transport.TransportProtocol;
import org.eclipse.jgit.transport.URIish;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class LambdaConfig implements Config {

    private static final LambdaConfig INSTANCE = new LambdaConfig();
    private static final TransportProtocol PROTO = new TransportAmazonLambdaS3.TransportProtocolS3(LambdaConfig.get().client);

    private static final String ENV_REMOTE = "env.remote";
    private static final String ENV_BRANCH = "env.branch";
    private static final String ENV_BUCKET = "env.bucket";
    private static final String ENV_GITHUB = "env.github";
    private final Properties props = new Properties();
    private final AmazonS3 client = new AmazonS3Client();
    private final TransportConfigCallback authentication;
    private final Remote remote;
    private final Branch branch;
    private final FileRepository repository;

    private LambdaConfig() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("env.properties")) {
            this.props.load(is);
            this.repository = new FileRepository(new File(System.getProperty("java.io.tmpdir"), "s3"));
        }
        catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        overwriteWithSystemProperty(ENV_REMOTE);
        overwriteWithSystemProperty(ENV_BRANCH);
        overwriteWithSystemProperty(ENV_BUCKET);
        overwriteWithSystemProperty(ENV_GITHUB);

        this.remote = new Remote(props.getProperty(ENV_REMOTE, Constants.DEFAULT_REMOTE_NAME));
        this.branch = new Branch(props.getProperty(ENV_BRANCH, Constants.MASTER));
        this.authentication = new SecureShellAuthentication(new Bucket(props.getProperty(ENV_BUCKET)), client);
    }

    public static LambdaConfig get() {
        return INSTANCE;
    }

    @Override
    public CloneCommand configure(CloneCommand cmd) {
        return addAuthentication(cmd).setRemote(remote.getShortRef()).setGitDir(repository.getDirectory());
    }

    @Override
    public FetchCommand configure(FetchCommand cmd) {
        return addAuthentication(cmd).setRemote(remote.getShortRef());
    }

    @Override
    public PushCommand configure(PushCommand cmd) {
        return addAuthentication(cmd).setRemote(remote.getShortRef());
    }

    @Override
    public RemoteSetUrlCommand configure(RemoteSetUrlCommand cmd, URIish uri, boolean push) {
        cmd.setName(remote.getShortRef());
        cmd.setUri(uri);
        cmd.setPush(push);
        return cmd;
    }

    @Override
    public boolean isWatchedBranch(Branch branch) {
        return this.branch.equals(branch);
    }

    @Override
    public URIish getFetchUrl() {
        return new URIish().setScheme("ssh").setUser("git").setHost("github.com").setPath(props.getProperty(ENV_GITHUB));
    }

    @Override
    public SyncableRepository getPushRepository() {
        return new RepositoryS3(new Bucket(props.getProperty(ENV_BUCKET)), repository, client, branch);
    }

    @Override
    public Repository getWorkingFileRepository() {
        return repository;
    }

    private void overwriteWithSystemProperty(String value) {
        String prop = System.getenv(value.replace(".", "_"));
        if (StringUtils.hasValue(prop)) {
            this.props.setProperty(value, prop);
        }
    }

    private <C extends GitCommand, T extends TransportCommand<C, ?>> C addAuthentication(T cmd) {
        return cmd.setTransportConfigCallback(authentication);
    }

    static {
        // Register the new transport for jgit.
        Transport.register(PROTO);
    }
}
