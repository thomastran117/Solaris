package backend.kafka.consumers;

import backend.events.email.EmailEvent;
import backend.kafka.workers.EmailSender;
import backend.services.intf.CacheService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailKafkaConsumerTest {

    private static final String TOPIC = "email-events";

    private EmailSender  sender;
    private CacheService cacheService;
    private EmailKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        sender      = mock(EmailSender.class);
        cacheService = mock(CacheService.class);
        consumer    = new EmailKafkaConsumer(sender, cacheService);
    }

    @Test
    void onEmailEvent_lockAcquired_callsSender() {
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        EmailEvent event = emailEvent();

        consumer.onEmailEvent(event, TOPIC, 0, 1L);

        verify(sender).send(event);
    }

    @Test
    void onEmailEvent_duplicateOffset_skipsSender() {
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(false);

        consumer.onEmailEvent(emailEvent(), TOPIC, 0, 2L);

        verify(sender, never()).send(any(EmailEvent.class));
    }

    @Test
    void onEmailEvent_redisThrows_proceedsWithoutDedupAndCallsSender() {
        when(cacheService.tryLock(anyString(), anyString(), anyLong()))
                .thenThrow(new RuntimeException("Redis down"));
        EmailEvent event = emailEvent();

        assertDoesNotThrow(() -> consumer.onEmailEvent(event, TOPIC, 0, 3L));

        verify(sender).send(event);
    }

    @Test
    void onEmailEvent_dedupKeyUsesTopicPartitionOffset() {
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);

        consumer.onEmailEvent(emailEvent(), TOPIC, 2, 42L);

        verify(cacheService).tryLock(
                "email:dedup:" + TOPIC + ":2:42",
                anyString(),
                anyLong());
    }

    private static EmailEvent emailEvent() {
        return mock(EmailEvent.class);
    }
}
