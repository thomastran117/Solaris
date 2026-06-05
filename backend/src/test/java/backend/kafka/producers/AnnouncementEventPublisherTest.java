package backend.kafka.producers;

import backend.events.announcement.AnnouncementPublishedEvent;
import backend.models.enums.AnnouncementType;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnnouncementEventPublisherTest {

    private static final UUID ANNOUNCEMENT_ID = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final String TOPIC = "announcement-events";

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, AnnouncementPublishedEvent> kafkaTemplate;
    private AnnouncementEventPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        publisher = new AnnouncementEventPublisher(kafkaTemplate, TOPIC);
    }

    private AnnouncementPublishedEvent event() {
        return new AnnouncementPublishedEvent(
                ANNOUNCEMENT_ID, COMPANY_ID, "ShopWave",
                "New Product!", "Check it out.", AnnouncementType.NEW_PRODUCT,
                Instant.now());
    }

    @Test
    void publish_outsideTransaction_sendsDirectly() {
        // No transaction context active → doSend called immediately
        publisher.publish(event());

        ArgumentCaptor<AnnouncementPublishedEvent> captor =
                ArgumentCaptor.forClass(AnnouncementPublishedEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(COMPANY_ID.toString()), captor.capture());
        assertEquals(ANNOUNCEMENT_ID, captor.getValue().announcementId());
    }

    @Test
    void publish_kafkaThrows_doesNotPropagate() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka down"));
        assertDoesNotThrow(() -> publisher.publish(event()));
    }
}
