package backend.kafka.consumers;

import backend.events.notification.NotificationEvent;
import backend.kafka.workers.NotificationDispatchWorker;
import backend.services.intf.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class NotificationKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaConsumer.class);
    private static final long DEDUP_TTL_SECONDS = 86_400L; // 24 h

    private final NotificationDispatchWorker dispatchWorker;
    private final CacheService cacheService;

    public NotificationKafkaConsumer(NotificationDispatchWorker dispatchWorker, CacheService cacheService) {
        this.dispatchWorker = dispatchWorker;
        this.cacheService = cacheService;
    }

    @KafkaListener(topics = "${app.kafka.topics.notification-events}",
                   groupId = "shopwave-notification-consumer",
                   containerFactory = "notificationKafkaListenerContainerFactory")
    public void onNotificationEvent(NotificationEvent event,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                    @Header(KafkaHeaders.OFFSET) long offset) {
        String dedupKey = "notif:dedup:" + topic + ":" + partition + ":" + offset;
        try {
            if (!cacheService.tryLock(dedupKey, "1", DEDUP_TTL_SECONDS)) {
                log.info("[NOTIFY] Skipping duplicate delivery t={} p={} o={}", topic, partition, offset);
                return;
            }
        } catch (Exception e) {
            log.warn("[NOTIFY] Dedup check failed, proceeding without dedup: {}", e.getMessage());
        }
        dispatchWorker.dispatch(event);
    }
}
