package net.berla.aws.git;

import com.amazonaws.util.StringUtils;
import org.eclipse.jgit.lib.Constants;

/**
 * The name of a remote.
 *
 * @author Matthias Berla (matthias@berla.net)
 * @version $Revision$ $Date$
 */
public class Remote {

    private final String remote;

    public Remote(String remote) {
        if (StringUtils.isNullOrEmpty(remote)) {
            throw new IllegalArgumentException();
        }
        this.remote = remote.startsWith(Constants.R_REMOTES) ? remote.substring(Constants.R_REMOTES.length()) : remote;
    }

    public String getShortRef() {
        return remote;
    }

    public String getFullRef() {
        return Constants.R_REMOTES + remote;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Remote rhs = (Remote) o;
        return remote.equals(rhs.remote);
    }

    @Override
    public int hashCode() {
        return remote.hashCode();
    }

    public String getRef(Branch branch) {
        return getFullRef() + "/" + branch.getShortRef();
    }
}
