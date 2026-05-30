package backend.kafka.consumers;

import backend.events.email.EmailEvent;
import backend.kafka.workers.EmailSender;
import backend.services.intf.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class EmailKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailKafkaConsumer.class);
    private static final long DEDUP_TTL_SECONDS = 86_400L; // 24 h

    private final EmailSender sender;
    private final CacheService cacheService;

    public EmailKafkaConsumer(EmailSender sender, CacheService cacheService) {
        this.sender = sender;
        this.cacheService = cacheService;
    }

    @KafkaListener(topics = "${app.kafka.topics.email-events}", groupId = "email-worker",
                   containerFactory = "emailKafkaListenerContainerFactory")
    public void onEmailEvent(EmailEvent event,
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                             @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                             @Header(KafkaHeaders.OFFSET) long offset) {
        // Dedup: if this exact offset was already processed (e.g. after a crash-before-commit
        // rebalance), skip re-sending to avoid duplicate emails. tryLock is a Redis SET NX.
        String dedupKey = "email:dedup:" + topic + ":" + partition + ":" + offset;
        try {
            if (!cacheService.tryLock(dedupKey, "1", DEDUP_TTL_SECONDS)) {
                log.info("[EMAIL] Skipping duplicate delivery t={} p={} o={}", topic, partition, offset);
                return;
            }
        } catch (Exception e) {
            // Redis unavailable — proceed without dedup rather than blocking email delivery.
            log.warn("[EMAIL] Dedup check failed, proceeding without dedup: {}", e.getMessage());
        }
        sender.send(event);
    }
}
