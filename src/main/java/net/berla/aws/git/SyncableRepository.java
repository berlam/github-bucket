package net.berla.aws.git;

import net.berla.aws.Status;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.transport.URIish;

import java.util.concurrent.Callable;

/**
 * A repository which is cloneable and has a syncable work tree.
 *
 * @author Matthias Berla (matthias@berla.net)
 * @version $Revision$ $Date$
 */
public interface SyncableRepository extends Callable<Status> {

    default CloneCommand clone(CloneCommand cmd) {
        return cmd.setURI(getUri().toASCIIString());
    }

    URIish getUri();
}
