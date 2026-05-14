package backend.kafka.consumers;

import backend.events.imports.ImportJobMessage;
import backend.services.intf.imports.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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
        }
    }
}
