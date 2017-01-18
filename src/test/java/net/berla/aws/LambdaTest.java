package net.berla.aws;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.util.IOUtils;
import net.berla.aws.git.Branch;
import org.apache.http.HttpStatus;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Callable;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LambdaTest {

    private final Config config = mock(Config.class);

    private final Callable<Status> worker = mock(Callable.class);

    private final Lambda uut = new Lambda(config, worker);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldWorkCompletely() throws Exception {
        // Given
        Context context = mock(Context.class);
        SNSEvent snsEvent = createSnsEvent("push");

        when(config.isWatchedBranch(new Branch("changes"))).thenReturn(true);
        when(worker.call()).thenReturn(Status.SUCCESS);

        // When
        Integer response = uut.handleRequest(snsEvent, context);

        // Then
        assertThat(response, is(HttpStatus.SC_OK));
        verify(config, times(1)).isWatchedBranch(new Branch("changes"));
        verify(worker, times(1)).call();
    }

    @Test
    public void shouldFailOnWrongWorkerCall() throws Exception {
        // Given
        Context context = mock(Context.class);
        SNSEvent snsEvent = createSnsEvent("push");

        when(config.isWatchedBranch(new Branch("changes"))).thenReturn(true);
        when(worker.call()).thenReturn(Status.FAILED);

        // When
        Integer response = uut.handleRequest(snsEvent, context);

        // Then
        assertThat(response, is(HttpStatus.SC_BAD_REQUEST));
        verify(config, times(1)).isWatchedBranch(new Branch("changes"));
        verify(worker, times(1)).call();
    }

    @Test
    public void shouldFailOnOtherBranch() throws Exception {
        // Given
        Context context = mock(Context.class);
        SNSEvent snsEvent = createSnsEvent("push");

        when(config.isWatchedBranch(new Branch("changes"))).thenReturn(false);

        // When
        Integer response = uut.handleRequest(snsEvent, context);

        // Then
        assertThat(response, is(HttpStatus.SC_BAD_REQUEST));
        verify(config, times(1)).isWatchedBranch(any());
        verify(worker, times(0)).call();
    }

    @Test
    public void shouldFailOnOtherEvent() throws Exception {
        // Given
        Context context = mock(Context.class);
        SNSEvent snsEvent = createSnsEvent("commit");

        // When
        Integer response = uut.handleRequest(snsEvent, context);

        // Then
        assertThat(response, is(HttpStatus.SC_BAD_REQUEST));
        verify(config, times(0)).isWatchedBranch(any());
        verify(worker, times(0)).call();
    }

    @Test
    public void shouldFailOnOtherError() throws Exception {
        // Given
        Context context = mock(Context.class);
        SNSEvent snsEvent = createSnsEvent("push");

        doThrow(new IllegalArgumentException("Expected test exception")).when(config).isWatchedBranch(any());

        // When
        Integer response = uut.handleRequest(snsEvent, context);

        // Then
        assertThat(response, is(HttpStatus.SC_INTERNAL_SERVER_ERROR));
        verify(config, times(1)).isWatchedBranch(any());
        verify(worker, times(0)).call();
    }

    private SNSEvent createSnsEvent(final String githubEvent) {
        SNSEvent.SNS sns = new SNSEvent.SNS();
        sns.setMessageAttributes(new HashMap<String, SNSEvent.MessageAttribute>(1, 1) {
            {
                SNSEvent.MessageAttribute attr = new SNSEvent.MessageAttribute();
                attr.setValue(githubEvent);
                put("X-Github-Event", attr);
            }
        });
        try (InputStream is = getClass().getResourceAsStream("/github-push-payload.json")) {
            sns.setMessage(IOUtils.toString(is));
        }
        catch (IOException e) {
            throw new IllegalArgumentException(e);
        }

        SNSEvent.SNSRecord record = new SNSEvent.SNSRecord();
        record.setSns(sns);

        SNSEvent snsEvent = new SNSEvent();
        snsEvent.setRecords(Collections.singletonList(record));
        return snsEvent;
    }
}
