package net.berla.aws;

import net.berla.aws.environment.LambdaConfig;
import net.berla.aws.git.SyncableRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

/**
 * Worker to update a S3 repository with the Github repository.
 *
 * @author Matthias Berla (matthias@berla.net)
 * @version $Revision$ $Date$
 */
class Worker implements Callable<Status> {

    private static final Logger LOG = LoggerFactory.getLogger(Worker.class);

    private final Config config;
    private final URIish fetchUrl;
    private final SyncableRepository pushRepository;

    Worker(Config config) {
        this.config = config;
        this.fetchUrl = config.getFetchUrl();
        this.pushRepository = config.getPushRepository();
    }

    public static void main(String[] args) throws Exception {
        new Worker(LambdaConfig.get()).call();
    }

    @Override
    public Status call() throws Exception {
        LOG.info("Starting repository synchronization");
        try (Git git = getWorkingFileRepository()) {
            // Set fetch repo
            config.configure(git.remoteSetUrl(), fetchUrl, false).call();
            config.configure(git.remoteSetUrl(), pushRepository, true).call();

            // Fetch repo
            config.configure(git.fetch()).setCheckFetchedObjects(true).call();

            // Push repo
            config.configure(git.push()).setForce(true).call();

            // Sync working tree
            return pushRepository.call();
        }
        finally {
            LOG.info("Finished repository synchronization");
            // Close repository and free resources
            config.getWorkingFileRepository().close();
        }
    }

    private Git getWorkingFileRepository() throws GitAPIException {
        // Check for existing repo
        if (config.getWorkingFileRepository().getObjectDatabase().exists()) {
            return Git.wrap(config.getWorkingFileRepository());
        }
        // Clone new repo
        return config.configure(pushRepository.clone(Git.cloneRepository())).setBare(true).call();
    }
}
