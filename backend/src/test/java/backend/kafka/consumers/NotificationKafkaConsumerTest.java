package backend.kafka.consumers;

import backend.events.notification.NotificationEvent;
import backend.kafka.workers.NotificationDispatchWorker;
import backend.services.intf.CacheService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationKafkaConsumerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final String TOPIC = "notification-events";

    private NotificationDispatchWorker dispatchWorker;
    private CacheService cacheService;
    private NotificationKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        dispatchWorker = mock(NotificationDispatchWorker.class);
        cacheService = mock(CacheService.class);
        consumer = new NotificationKafkaConsumer(dispatchWorker, cacheService);
    }

    // ─── onNotificationEvent ─────────────────────────────────────────────────

    @Test
    void onNotificationEvent_happyPath_callsDispatch() {
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        NotificationEvent event = shipped();

        consumer.onNotificationEvent(event, TOPIC, 0, 0L);

        verify(dispatchWorker).dispatch(event);
    }

    @Test
    void onNotificationEvent_duplicateOffset_skipsDispatch() {
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(false);

        consumer.onNotificationEvent(shipped(), TOPIC, 0, 1L);

        verify(dispatchWorker, never()).dispatch(any());
    }

    @Test
    void onNotificationEvent_dedupRedisFailure_proceedsWithoutDedup() {
        when(cacheService.tryLock(anyString(), anyString(), anyLong()))
                .thenThrow(new RuntimeException("Redis down"));
        NotificationEvent event = shipped();

        assertDoesNotThrow(() -> consumer.onNotificationEvent(event, TOPIC, 0, 2L));

        verify(dispatchWorker).dispatch(event);
    }

    private NotificationEvent shipped() {
        return new NotificationEvent.OrderShipped(USER_ID, TestIds.uuid(2), "Alice", "TRK1", "UPS");
    }
}
