package net.berla.aws;

import net.berla.aws.git.Branch;
import net.berla.aws.git.SyncableRepository;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteSetUrlCommand;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;

/**
 * The environment configuration.
 *
 * @author Matthias Berla (matthias@berla.net)
 * @version $Revision$ $Date$
 */
public interface Config {

    default CloneCommand configure(CloneCommand cmd) {
        return cmd;
    }

    default FetchCommand configure(FetchCommand cmd) {
        return cmd;
    }

    default PushCommand configure(PushCommand cmd) {
        return cmd;
    }

    default RemoteSetUrlCommand configure(RemoteSetUrlCommand cmd, URIish uri, boolean push) {
        return cmd;
    }

    default RemoteSetUrlCommand configure(RemoteSetUrlCommand cmd, SyncableRepository repo, boolean push) {
        return configure(cmd, repo.getUri(), push);
    }

    boolean isWatchedBranch(Branch branch);

    URIish getFetchUrl();

    SyncableRepository getPushRepository();

    Repository getWorkingFileRepository();
}
