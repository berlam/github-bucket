package net.berla.aws.git;

import org.eclipse.jgit.lib.Constants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class RemoteTest {

    @Test
    public void shouldBeValid() throws Exception {
        Remote remote = new Remote(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME);
        assertThat(remote.getShortRef(), is(Constants.DEFAULT_REMOTE_NAME));
        assertThat(remote.getFullRef(), is(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME));
    }
}
