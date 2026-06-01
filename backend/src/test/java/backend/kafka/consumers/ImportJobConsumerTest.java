package backend.kafka.consumers;

import backend.events.imports.ImportJobMessage;
import backend.services.intf.imports.ImportService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ImportJobConsumerTest {

    private static final UUID JOB_ID     = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final UUID UPLOADER   = TestIds.uuid(3);

    private ImportService importService;
    private ImportJobConsumer consumer;

    @BeforeEach
    void setUp() {
        importService = mock(ImportService.class);
        consumer = new ImportJobConsumer(importService);
    }

    @Test
    void onImportJob_happyPath_callsProcessJob() {
        consumer.onImportJob(message());

        verify(importService).processJob(JOB_ID);
        verify(importService, never()).markJobFailed(any(), anyString());
    }

    @Test
    void onImportJob_processJobThrows_marksJobFailed() {
        doThrow(new RuntimeException("parse error")).when(importService).processJob(JOB_ID);

        assertDoesNotThrow(() -> consumer.onImportJob(message()));

        verify(importService).markJobFailed(eq(JOB_ID), anyString());
    }

    @Test
    void onImportJob_processJobAndMarkFailedBothThrow_noExceptionPropagated() {
        doThrow(new RuntimeException("parse error")).when(importService).processJob(JOB_ID);
        doThrow(new RuntimeException("DB down")).when(importService).markJobFailed(any(), anyString());

        assertDoesNotThrow(() -> consumer.onImportJob(message()));
    }

    private ImportJobMessage message() {
        return new ImportJobMessage(JOB_ID, COMPANY_ID, UPLOADER);
    }
}
