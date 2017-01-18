package net.berla.aws.git;

import org.eclipse.jgit.lib.Constants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class BranchTest {

    @Test
    public void shouldBeValid() throws Exception {
        Branch branch = new Branch(Constants.R_HEADS + Constants.MASTER);
        assertThat(branch.getShortRef(), is(Constants.MASTER));
        assertThat(branch.getFullRef(), is(Constants.R_HEADS + Constants.MASTER));
    }
}
