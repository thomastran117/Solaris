package backend.kafka.producers;

import backend.events.ImportJobRequestedEvent;
import backend.events.imports.ImportJobMessage;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ImportJobPublisherTest {

    private static final UUID JOB_ID = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final UUID USER_ID = TestIds.uuid(3);
    private static final String TOPIC = "import-jobs";

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, ImportJobMessage> kafkaTemplate;
    private ImportJobPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        publisher = new ImportJobPublisher(kafkaTemplate, TOPIC);
    }

    @Test
    void onImportJobRequested_sendsMessage() {
        publisher.onImportJobRequested(new ImportJobRequestedEvent(JOB_ID, COMPANY_ID, USER_ID));

        ArgumentCaptor<ImportJobMessage> captor = ArgumentCaptor.forClass(ImportJobMessage.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(JOB_ID.toString()), captor.capture());
        ImportJobMessage msg = captor.getValue();
        assertEquals(JOB_ID, msg.jobId());
        assertEquals(COMPANY_ID, msg.companyId());
        assertEquals(USER_ID, msg.uploadedBy());
    }

    @Test
    void onImportJobRequested_kafkaThrows_doesNotPropagate() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka down"));

        assertDoesNotThrow(() -> publisher.onImportJobRequested(
                new ImportJobRequestedEvent(JOB_ID, COMPANY_ID, USER_ID)));
    }
}
