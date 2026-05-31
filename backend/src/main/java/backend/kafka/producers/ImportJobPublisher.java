package backend.kafka.producers;

import backend.events.ImportJobRequestedEvent;
import backend.events.imports.ImportJobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays in-process {@link ImportJobRequestedEvent}s to the Kafka {@code import-jobs}
 * topic after the originating DB transaction commits — so the worker never sees a
 * job id that isn't yet visible.
 */
@Component
public class ImportJobPublisher {

    private static final Logger log = LoggerFactory.getLogger(ImportJobPublisher.class);

    private final KafkaTemplate<String, ImportJobMessage> kafkaTemplate;
    private final String topic;

    public ImportJobPublisher(
            KafkaTemplate<String, ImportJobMessage> kafkaTemplate,
            @Value("${app.kafka.topics.import-jobs}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onImportJobRequested(ImportJobRequestedEvent event) {
        try {
            var payload = new ImportJobMessage(event.jobId(), event.companyId(), event.uploadedBy());
            kafkaTemplate.send(topic, String.valueOf(event.jobId()), payload).whenComplete((res, ex) -> {
                if (ex != null) {
                    log.warn("import-jobs publish failed jobId={}", event.jobId(), ex);
                }
            });
        } catch (Throwable t) {
            log.warn("import-jobs publish error jobId={}", event.jobId(), t);
        }
    }
}
