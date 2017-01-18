package net.berla.aws;

import net.berla.aws.git.SyncableRepository;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.runners.MockitoJUnitRunner;

import static net.berla.aws.Status.FAILED;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class WorkerTest {

    private final Config config = mock(Config.class);

    private final Worker uut;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    public WorkerTest() {
        URIish fetchUrl = new URIish();
        SyncableRepository pushRepository = mock(SyncableRepository.class);

        when(config.getFetchUrl()).thenReturn(fetchUrl);
        when(config.getPushRepository()).thenReturn(pushRepository);

        ObjectDatabase database = mock(ObjectDatabase.class);
        when(database.exists()).thenReturn(false);
        Repository repository = mock(Repository.class);
        when(repository.getObjectDatabase()).thenReturn(database);
        when(config.getWorkingFileRepository()).thenReturn(repository);

        uut = new Worker(config);
    }

    @Test
    public void shouldProcessSuccessful() throws Exception {
        // Given
        URIish fetchUrl = config.getFetchUrl();
        SyncableRepository pushRepository = config.getPushRepository();
        Git git = mock(Git.class);
        doNothing().when(git).close();

        CloneCommand cloneCommand = mock(CloneCommand.class);
        when(cloneCommand.setBare(true)).thenReturn(cloneCommand);
        when(cloneCommand.call()).thenReturn(git);
        when(pushRepository.clone(any())).thenReturn(cloneCommand);
        when(config.configure(cloneCommand)).thenReturn(cloneCommand);

        RemoteSetUrlCommand remoteSetUrlCommand = mock(RemoteSetUrlCommand.class);
        when(remoteSetUrlCommand.call()).thenReturn(null);
        when(git.remoteSetUrl()).thenReturn(remoteSetUrlCommand);
        when(config.configure(remoteSetUrlCommand, fetchUrl, false)).thenReturn(remoteSetUrlCommand);
        when(config.configure(remoteSetUrlCommand, pushRepository, true)).thenReturn(remoteSetUrlCommand);

        FetchCommand fetchCommand = mock(FetchCommand.class);
        when(fetchCommand.setCheckFetchedObjects(true)).thenReturn(fetchCommand);
        when(fetchCommand.call()).thenReturn(null);
        when(git.fetch()).thenReturn(fetchCommand);
        when(config.configure(fetchCommand)).thenReturn(fetchCommand);

        PushCommand pushCommand = mock(PushCommand.class);
        when(pushCommand.setForce(true)).thenReturn(pushCommand);
        when(pushCommand.call()).thenReturn(null);
        when(git.push()).thenReturn(pushCommand);
        when(config.configure(pushCommand)).thenReturn(pushCommand);

        when(pushRepository.call()).thenReturn(Status.SUCCESS);

        // When
        Status status = uut.call();

        // Then
        assertThat(status, is(Status.SUCCESS));
        InOrder inOrder = inOrder(config, pushRepository);
        inOrder.verify(config, times(1)).configure(cloneCommand);
        inOrder.verify(config, times(1)).configure(remoteSetUrlCommand, fetchUrl, false);
        inOrder.verify(config, times(1)).configure(remoteSetUrlCommand, pushRepository, true);
        inOrder.verify(config, times(1)).configure(fetchCommand);
        inOrder.verify(config, times(1)).configure(pushCommand);
        inOrder.verify(pushRepository, times(1)).call();
    }

    @Test
    public void shouldFailOnException() throws Exception {
        // Given
        SyncableRepository pushRepository = config.getPushRepository();
        Git git = mock(Git.class);
        doNothing().when(git).close();

        CloneCommand cloneCommand = mock(CloneCommand.class);
        when(cloneCommand.setBare(true)).thenReturn(cloneCommand);
        when(cloneCommand.call()).thenThrow(new TransportException("Expected test exception"));
        when(pushRepository.clone(any())).thenReturn(cloneCommand);
        when(config.configure(cloneCommand)).thenReturn(cloneCommand);

        exception.expect(TransportException.class);
        exception.expectMessage("Expected test exception");

        // When
        Status status = uut.call();

        // Then
        assertThat(status, is(FAILED));
    }
}
