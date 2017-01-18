package net.berla.aws.git;

import com.amazonaws.util.StringUtils;
import org.eclipse.jgit.lib.Constants;

/**
 * The reference to a branch.
 *
 * @author Matthias Berla (matthias@berla.net)
 * @version $Revision$ $Date$
 */
public class Branch {

    private final String branch;

    public Branch(String branch) {
        if (StringUtils.isNullOrEmpty(branch)) {
            throw new IllegalArgumentException();
        }
        this.branch = branch.startsWith(Constants.R_HEADS) ? branch.substring(Constants.R_HEADS.length()) : branch;
    }

    public String getShortRef() {
        return branch;
    }

    public String getFullRef() {
        return Constants.R_HEADS + branch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Branch rhs = (Branch) o;
        return branch.equals(rhs.branch);
    }

    @Override
    public int hashCode() {
        return branch.hashCode();
    }
}
