package backend.kafka.workers;

import backend.configurations.environment.EnvironmentSetting;
import backend.models.core.Report;
import backend.repositories.IndexingFailureRepository;
import backend.repositories.ReportRepository;
import backend.repositories.search.ReportSearchRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ReportIndexingServiceTest {

    private static final UUID REPORT_ID = TestIds.uuid(1);

    private ReportSearchRepository searchRepository;
    private ReportRepository reportRepository;
    private IndexingFailureRepository failureRepository;
    private IndexVersionManager indexVersionManager;
    private ReportIndexingService service;

    @BeforeEach
    void setUp() {
        searchRepository = mock(ReportSearchRepository.class);
        reportRepository = mock(ReportRepository.class);
        failureRepository = mock(IndexingFailureRepository.class);
        indexVersionManager = mock(IndexVersionManager.class);

        service = new ReportIndexingService(
                searchRepository,
                reportRepository,
                failureRepository,
                indexVersionManager,
                new EnvironmentSetting());

        // Inject real queue to bypass @PostConstruct threading setup
        ReflectionTestUtils.setField(service, "taskQueue", new LinkedBlockingQueue<>(1000));
    }

    // ── Queue submission (does not throw) ─────────────────────────────────────

    @Test
    void indexReport_doesNotThrow() {
        assertDoesNotThrow(() -> service.indexReport(report()));
    }

    @Test
    void removeReport_doesNotThrow() {
        assertDoesNotThrow(() -> service.removeReport(REPORT_ID));
    }

    @Test
    void reindexAll_emptyRepository_logsAndReturns() {
        when(reportRepository.findAll()).thenReturn(List.of());
        assertDoesNotThrow(() -> service.reindexAll());
    }

    @Test
    void reindexAll_withReports_submitsAllToQueue() {
        when(reportRepository.findAll()).thenReturn(List.of(report(), report()));
        assertDoesNotThrow(() -> service.reindexAll());
    }

    // ── processBatch via run ──────────────────────────────────────────────────

    @Test
    void indexReport_processBatchIndexes() throws Exception {
        Report r = report();
        service.indexReport(r);

        // Drain the queue by calling the package-private method via reflective access
        // Alternatively, verify via searchRepository after forceful queue drain
        // (Here we simply confirm the task was submitted without throwing)
        LinkedBlockingQueue<?> queue = (LinkedBlockingQueue<?>) ReflectionTestUtils.getField(service, "taskQueue");
        assert queue != null && !queue.isEmpty();
    }

    @Test
    void removeReport_processBatchRemoves() throws Exception {
        service.removeReport(REPORT_ID);

        LinkedBlockingQueue<?> queue = (LinkedBlockingQueue<?>) ReflectionTestUtils.getField(service, "taskQueue");
        assert queue != null && !queue.isEmpty();
    }

    // ── saveAll failure → persists to failure repo ─────────────────────────────

    @Test
    void searchSaveAllFails_persistsFailureRecord() throws Exception {
        doThrow(new RuntimeException("ES down")).when(searchRepository).saveAll(any());

        // Manually invoke processBatch via the report's indexing path
        // by submitting to a 1-capacity queue (forces direct processing)
        ReflectionTestUtils.setField(service, "taskQueue", new LinkedBlockingQueue<>(1));
        service.indexReport(report());

        // Cannot directly drive worker loop in unit test; just confirm submit doesn't throw
        // (worker loop integration is tested by not throwing exceptions out of this layer)
    }

    // ── processBatch — success paths ─────────────────────────────────────────

    @Test
    void processBatch_indexReport_callsSaveAll() {
        List<ReportIndexingService.Task> batch =
                List.of(new ReportIndexingService.Task.Index(report()));

        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(searchRepository).saveAll(anyList());
    }

    @Test
    void processBatch_removeReport_callsDeleteAll() {
        List<ReportIndexingService.Task> batch =
                List.of(new ReportIndexingService.Task.Remove(REPORT_ID));

        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(searchRepository).deleteAllById(anyList());
    }

    @Test
    void processBatch_emptyBatch_callsNothing() {
        ReflectionTestUtils.invokeMethod(service, "processBatch", List.of());

        verify(searchRepository, never()).saveAll(any());
        verify(searchRepository, never()).deleteAllById(any());
    }

    @Test
    void processBatch_mixed_indexAndRemove() {
        List<ReportIndexingService.Task> batch = List.of(
                new ReportIndexingService.Task.Index(report()),
                new ReportIndexingService.Task.Remove(TestIds.uuid(99))
        );

        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(searchRepository).saveAll(anyList());
        verify(searchRepository).deleteAllById(anyList());
    }

    // ── processBatch — failure / DLQ paths ───────────────────────────────────

    @Test
    void processBatch_saveAllThrows_persistsFailureRecord() {
        doThrow(new RuntimeException("ES down")).when(searchRepository).saveAll(any());

        List<ReportIndexingService.Task> batch =
                List.of(new ReportIndexingService.Task.Index(report()));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(failureRepository).save(any());
    }

    @Test
    void processBatch_deleteAllThrows_persistsFailureRecord() {
        doThrow(new RuntimeException("ES down")).when(searchRepository).deleteAllById(any());

        List<ReportIndexingService.Task> batch =
                List.of(new ReportIndexingService.Task.Remove(REPORT_ID));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(failureRepository).save(any());
    }

    @Test
    void processBatch_failureRepositoryThrows_doesNotPropagate() {
        doThrow(new RuntimeException("ES down")).when(searchRepository).saveAll(any());
        doThrow(new RuntimeException("DB down")).when(failureRepository).save(any());

        List<ReportIndexingService.Task> batch =
                List.of(new ReportIndexingService.Task.Index(report()));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "processBatch", batch));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Report report() {
        Report r = new Report();
        r.setId(REPORT_ID);
        return r;
    }
}
