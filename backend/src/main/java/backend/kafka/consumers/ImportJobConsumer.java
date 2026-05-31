package backend.kafka.consumers;

import backend.events.imports.ImportJobMessage;
import backend.services.intf.imports.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka entry point for the import worker. The worker itself is idempotent
 * (see {@link ImportService#processJob}), so re-delivery is safe. A previous version
 * caught and swallowed worker exceptions, which left failed jobs visible only via a
 * log line — operators had to grep to find them. Now any worker exception is marked
 * on the {@code ImportJob} row itself (status=FAILED, with the cause as failureReason)
 * so the existing UI surfaces it as a queryable, operator-reviewable record.
 *
 * <p>Beyond this in-DB dead-letter, Spring Kafka's broker-level DLQ
 * ({@code DeadLetterPublishingRecoverer}) is the right next step if/when the team
 * decides to route to a dedicated {@code import-jobs.dlq} topic for replay. Adding
 * that affects all listeners on this container factory, so it's left as a separate
 * config change.
 */
@Component
public class ImportJobConsumer {

    private static final Logger log = LoggerFactory.getLogger(ImportJobConsumer.class);

    private final ImportService importService;

    public ImportJobConsumer(ImportService importService) {
        this.importService = importService;
    }

    @KafkaListener(topics = "${app.kafka.topics.import-jobs}", groupId = "imports-worker")
    public void onImportJob(ImportJobMessage message) {
        try {
            importService.processJob(message.jobId());
        } catch (Exception ex) {
            log.error("[IMPORT] worker failed for jobId={}", message.jobId(), ex);
            // Record the failure in the operator-visible job state so it isn't lost.
            // Wrapped in its own try because a DB outage here must not loop the consumer.
            try {
                importService.markJobFailed(message.jobId(),
                        "Worker exception: " + ex.getClass().getSimpleName() + " — " + ex.getMessage());
            } catch (Exception markEx) {
                log.error("[IMPORT] also failed to mark jobId={} as FAILED: {}",
                        message.jobId(), markEx.getMessage());
            }
        }
    }
}
