package backend.kafka.producers;

import backend.events.loyalty.LoyaltyEvent;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoyaltyEventPublisherImplTest {

    private static final UUID USER_ID    = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final String TOPIC    = "loyalty-events";

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, LoyaltyEvent> kafkaTemplate;
    private LoyaltyEventPublisherImpl publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new LoyaltyEventPublisherImpl(kafkaTemplate, TOPIC);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    @Test
    void publish_pointsEarned_sendsWithUserIdAsKey() {
        LoyaltyEvent event = new LoyaltyEvent.PointsEarned(USER_ID, COMPANY_ID, 100L, "EARN_ORDER", 500L);

        publisher.publish(event);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), keyCaptor.capture(), eq(event));
        assertEquals(USER_ID.toString(), keyCaptor.getValue());
    }

    @Test
    void publish_tierUpgraded_sendsWithUserIdAsKey() {
        LoyaltyEvent event = new LoyaltyEvent.TierUpgraded(USER_ID, COMPANY_ID, "Gold", "Silver");

        publisher.publish(event);

        verify(kafkaTemplate).send(eq(TOPIC), eq(USER_ID.toString()), eq(event));
    }

    @Test
    void publish_pointsExpiringSoon_sendsWithUserIdAsKey() {
        LoyaltyEvent event = new LoyaltyEvent.PointsExpiringSoon(
                USER_ID, COMPANY_ID, 200L, Instant.now().plusSeconds(3600));

        publisher.publish(event);

        verify(kafkaTemplate).send(eq(TOPIC), eq(USER_ID.toString()), eq(event));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_sendFails_exceptionSwallowed() {
        LoyaltyEvent event = new LoyaltyEvent.PointsEarned(USER_ID, COMPANY_ID, 100L, "EARN_ORDER", 500L);
        CompletableFuture<SendResult<String, LoyaltyEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        publisher.publish(event); // must not throw
    }

    @Test
    void publish_kafkaThrowsSynchronously_exceptionSwallowed() {
        LoyaltyEvent event = new LoyaltyEvent.TierUpgraded(USER_ID, COMPANY_ID, "Gold", "Silver");
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka broker unavailable"));

        publisher.publish(event); // must not throw
    }
}
