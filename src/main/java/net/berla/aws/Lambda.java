package net.berla.aws;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.berla.aws.environment.LambdaConfig;
import net.berla.aws.git.Branch;
import net.berla.aws.github.event.PushPayload;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

import static java.lang.String.format;

/**
 * The Main-Class for the Lambda-Function.
 * Receives the {@link SNSEvent}, parses the {@link PushPayload} and triggers the {@link Worker}.
 *
 * @author Matthias Berla (matthias@berla.net)
 * @version $Revision$ $Date$
 */
public class Lambda implements RequestHandler<SNSEvent, Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(Lambda.class);

    private static final ObjectMapper MAPPER = Jackson.getObjectMapper();
    private static final String X_GITHUB_EVENT = "X-Github-Event";
    private static final String EVENT_PUSH = "push";

    private final Config config;
    private final Callable<Status> worker;

    /**
     * Empty constructor for Lambda execution.
     */
    public Lambda() {
        this(LambdaConfig.get());
    }

    /**
     * Configurable constructor for dynamic creation.
     *
     * @param config The configuration
     */
    public Lambda(Config config) {
        this(config, new Worker(config));
    }

    /**
     * Configurable constructor for dynamic creation with custom worker.
     *
     * @param config The configuration
     * @param worker The worker
     */
    public Lambda(Config config, Callable<Status> worker) {
        this.config = config;
        this.worker = worker;
    }

    public Integer handleRequest(SNSEvent event, Context context) {
        try {
            // SNS Events could be possible more than one even if this looks a bit unusual for the deploy case.
            for (SNSEvent.SNSRecord record : event.getRecords()) {
                SNSEvent.SNS sns = record.getSNS();
                // Check SNS header for event type.
                SNSEvent.MessageAttribute attr = sns.getMessageAttributes().get(X_GITHUB_EVENT);
                // Only watch pushes to master.
                if (EVENT_PUSH.equalsIgnoreCase(attr.getValue())) {
                    PushPayload value = MAPPER.readValue(sns.getMessage(), PushPayload.class);
                    if (config.isWatchedBranch(new Branch(value.getRef()))) {
                        LOG.info(format("Processing '%s' on '%s': '%s'", attr.getValue(), value.getRef(), value.getHeadCommit().getId()));
                        switch (worker.call()) {
                            case SUCCESS:
                                return HttpStatus.SC_OK;
                            case FAILED:
                                return HttpStatus.SC_BAD_REQUEST;
                        }
                    }
                    // Different branch was found.
                    else {
                        LOG.info(format("Push received for: '%s'", value.getRef()));
                    }
                }
                // Different event was found.
                else {
                    LOG.info(format("Event was: '%s'", attr.getValue()));
                }
            }
        }
        catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return HttpStatus.SC_INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.SC_BAD_REQUEST;
    }
}
