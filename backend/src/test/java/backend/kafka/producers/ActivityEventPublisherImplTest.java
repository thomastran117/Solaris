package backend.kafka.producers;

import backend.events.activity.ActivityType;
import backend.events.activity.UserActivityEvent;
import backend.services.intf.CacheService;
import backend.services.intf.profile.UserPreferenceService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityEventPublisherImplTest {

    private static final UUID USER_ID        = TestIds.uuid(1);
    private static final UUID PRODUCT_ID     = TestIds.uuid(2);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(3);
    private static final String TOPIC        = "user-activity";

    private KafkaTemplate<String, UserActivityEvent> kafkaTemplate;
    private UserPreferenceService userPreferenceService;
    private CacheService          cacheService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate         = mock(KafkaTemplate.class);
        userPreferenceService = mock(UserPreferenceService.class);
        cacheService          = mock(CacheService.class);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    private ActivityEventPublisherImpl publisher(boolean trackingEnabled) {
        return new ActivityEventPublisherImpl(
                kafkaTemplate, userPreferenceService, cacheService, TOPIC, trackingEnabled);
    }

    @Test
    void publish_trackingDisabled_doesNotSend() {
        ActivityEventPublisherImpl pub = publisher(false);

        pub.publish(viewEvent(USER_ID, MARKETPLACE_ID));

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void publish_nullMarketplaceId_doesNotSend() {
        ActivityEventPublisherImpl pub = publisher(true);

        pub.publish(viewEvent(USER_ID, null)); // null marketplaceId

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void publish_userOptedOut_doesNotSend() {
        when(userPreferenceService.isTrackingOptedOut(USER_ID)).thenReturn(true);
        ActivityEventPublisherImpl pub = publisher(true);

        pub.publish(viewEvent(USER_ID, MARKETPLACE_ID));

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void publish_viewEvent_dedupLockAcquired_sends() {
        when(userPreferenceService.isTrackingOptedOut(USER_ID)).thenReturn(false);
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        ActivityEventPublisherImpl pub = publisher(true);

        pub.publish(viewEvent(USER_ID, MARKETPLACE_ID));

        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void publish_viewEvent_dedupLockNotAcquired_doesNotSend() {
        when(userPreferenceService.isTrackingOptedOut(USER_ID)).thenReturn(false);
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(false);
        ActivityEventPublisherImpl pub = publisher(true);

        pub.publish(viewEvent(USER_ID, MARKETPLACE_ID));

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void publish_nonViewEvent_noDedup_alwaysSends() {
        when(userPreferenceService.isTrackingOptedOut(USER_ID)).thenReturn(false);
        ActivityEventPublisherImpl pub = publisher(true);

        pub.publish(event(USER_ID, MARKETPLACE_ID, ActivityType.ORDER));

        verify(cacheService, never()).tryLock(anyString(), anyString(), anyLong());
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void publish_sendFails_exceptionSwallowed() {
        when(userPreferenceService.isTrackingOptedOut(USER_ID)).thenReturn(false);
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        CompletableFuture<SendResult<String, UserActivityEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        ActivityEventPublisherImpl pub = publisher(true);
        pub.publish(viewEvent(USER_ID, MARKETPLACE_ID)); // must not throw
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private UserActivityEvent viewEvent(UUID userId, UUID marketplaceId) {
        return event(userId, marketplaceId, ActivityType.VIEW);
    }

    private UserActivityEvent event(UUID userId, UUID marketplaceId, ActivityType type) {
        return new UserActivityEvent(userId, null, PRODUCT_ID, marketplaceId, type, Instant.now());
    }
}
