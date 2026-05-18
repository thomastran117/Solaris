package backend.kafka.producers;

import backend.events.activity.ActivityType;
import backend.events.activity.UserActivityEvent;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.CacheService;
import backend.services.intf.profile.UserPreferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumMap;
import java.util.Map;

@Service
public class ActivityEventPublisherImpl implements ActivityEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ActivityEventPublisherImpl.class);

    // Dedup windows per activity type. 0 = disabled (business logic handles uniqueness for those types).
    // VIEW is the only type that needs a Redis guard — the frontend can fire it multiple times
    // per page load. All other types are naturally idempotent through DB-level uniqueness
    // constraints or single-use business flows (one review per user per product, etc.).
    private static final Map<ActivityType, Long> DEDUP_TTL_SECONDS;
    static {
        DEDUP_TTL_SECONDS = new EnumMap<>(ActivityType.class);
        DEDUP_TTL_SECONDS.put(ActivityType.VIEW, 30L);
    }

    private final KafkaTemplate<String, UserActivityEvent> kafkaTemplate;
    private final UserPreferenceService userPreferenceService;
    private final CacheService cacheService;
    private final String topic;
    private final boolean trackingEnabled;

    public ActivityEventPublisherImpl(
            KafkaTemplate<String, UserActivityEvent> kafkaTemplate,
            UserPreferenceService userPreferenceService,
            CacheService cacheService,
            @Value("${app.kafka.topics.user-activity}") String topic,
            @Value("${app.activity-tracking.enabled:true}") boolean trackingEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.userPreferenceService = userPreferenceService;
        this.cacheService = cacheService;
        this.topic = topic;
        this.trackingEnabled = trackingEnabled;
    }

    @Override
    public void publish(UserActivityEvent event) {
        if (!trackingEnabled) return;
        if (event.marketplaceId() == null || event.marketplaceId() <= 0) {
            log.debug("Skipping activity event — no marketplaceId: productId={} type={}", event.productId(), event.activityType());
            return;
        }
        if (event.userId() != null && userPreferenceService.isTrackingOptedOut(event.userId())) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(event);
                }
            });
        } else {
            doSend(event);
        }
    }

    private void doSend(UserActivityEvent event) {
        try {
            // Dedup check lives in doSend() so it runs after commit — a rolled-back
            // transaction never sets a key that would silently drop the retry.
            Long dedupTtl = DEDUP_TTL_SECONDS.get(event.activityType());
            if (dedupTtl != null && dedupTtl > 0) {
                String userKey = event.userId() != null ? "u" + event.userId() : "s" + event.sessionId();
                String dedupKey = "activity:dedup:" + event.activityType() + ":" + userKey + ":" + event.productId();
                if (!cacheService.tryLock(dedupKey, "1", dedupTtl)) {
                    log.debug("Dedup suppressed {} event userId={} productId={}", event.activityType(), event.userId(), event.productId());
                    return;
                }
            }

            String key = event.userId() != null ? String.valueOf(event.userId()) : event.sessionId();
            kafkaTemplate.send(topic, key, event).whenComplete((res, ex) -> {
                if (ex != null) {
                    log.warn("activity publish failed productId={} type={}", event.productId(), event.activityType(), ex);
                }
            });
        } catch (Throwable t) {
            log.warn("activity publish error productId={} type={}", event.productId(), event.activityType(), t);
        }
    }
}
