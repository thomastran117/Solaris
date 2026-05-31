package backend.kafka.workers;

import backend.configurations.environment.EnvironmentSetting;
import backend.documents.ReportDocument;
import backend.models.core.IndexingFailure;
import backend.models.core.Report;
import backend.repositories.IndexingFailureRepository;
import backend.repositories.ReportRepository;
import backend.repositories.search.ReportSearchRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ReportIndexingService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReportIndexingService.class);
    static final String DOC_TYPE = "REPORT";

    sealed interface Task permits Task.Index, Task.Remove {
        record Index(Report report) implements Task {}
        record Remove(UUID reportId) implements Task {}
    }

    private final ReportSearchRepository reportSearchRepository;
    private final ReportRepository reportRepository;
    private final IndexingFailureRepository failureRepository;
    private final IndexVersionManager indexVersionManager;
    private final EnvironmentSetting env;

    private volatile LinkedBlockingQueue<Task> taskQueue;
    private volatile ExecutorService workerPool;

    public ReportIndexingService(
            ReportSearchRepository reportSearchRepository,
            ReportRepository reportRepository,
            IndexingFailureRepository failureRepository,
            IndexVersionManager indexVersionManager,
            EnvironmentSetting env) {
        this.reportSearchRepository = reportSearchRepository;
        this.reportRepository = reportRepository;
        this.failureRepository = failureRepository;
        this.indexVersionManager = indexVersionManager;
        this.env = env;
    }

    @PostConstruct
    void startWorkers() {
        EnvironmentSetting.Elasticsearch.Indexing cfg = env.getElasticsearch().getIndexing();
        taskQueue = new LinkedBlockingQueue<>(cfg.getQueueCapacity());
        AtomicInteger idx = new AtomicInteger(0);
        workerPool = Executors.newFixedThreadPool(Math.max(1, cfg.getWorkerCount()), r -> {
            Thread t = new Thread(r, "report-index-worker-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < Math.max(1, cfg.getWorkerCount()); i++) {
            workerPool.submit(this::workerLoop);
        }
        log.info("[REPORT INDEX] Started {} worker(s)", cfg.getWorkerCount());
    }

    @PreDestroy
    void shutdown() {
        log.info("[REPORT INDEX] Shutting down — pending tasks: {}", taskQueue.size());
        workerPool.shutdownNow();
        try {
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("[REPORT INDEX] Workers did not terminate within 5s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    public void indexReport(Report report) {
        submit(new Task.Index(report));
    }

    public void removeReport(UUID reportId) {
        submit(new Task.Remove(reportId));
    }

    public void reindexAll() {
        List<Report> all = reportRepository.findAll();
        if (all.isEmpty()) {
            log.info("[REPORT INDEX] reindexAll — no reports");
            return;
        }
        for (Report r : all) submit(new Task.Index(r));
        log.info("[REPORT INDEX] Queued {} reports for full reindex", all.size());
    }

    private void submit(Task task) {
        if (!taskQueue.offer(task)) {
            log.warn("[REPORT INDEX] Queue full — dropping task: {}", task);
        }
    }

    // ---------------------------------------------------------------------
    // Startup — create index, queue initial population if empty
    // ---------------------------------------------------------------------

    @Override
    public void run(ApplicationArguments args) {
        indexVersionManager.ensureIndexExists("reports");
        try {
            if (reportSearchRepository.count() == 0) {
                reindexAll();
                log.info("[REPORT INDEX] Queued existing reports for initial indexing");
            }
        } catch (Exception e) {
            log.warn("[REPORT INDEX] Startup reindex skipped: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // Worker loop
    // ---------------------------------------------------------------------

    private void workerLoop() {
        int batchSize = env.getElasticsearch().getIndexing().getBatchSize();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Task first = taskQueue.poll(200, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                List<Task> batch = new ArrayList<>(batchSize);
                batch.add(first);
                taskQueue.drainTo(batch, batchSize - 1);
                processBatch(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("[REPORT INDEX] Worker error: {}", e.getMessage());
            }
        }
        List<Task> remaining = new ArrayList<>();
        taskQueue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            try { processBatch(remaining); }
            catch (Exception e) { log.warn("[REPORT INDEX] Shutdown drain error: {}", e.getMessage()); }
        }
    }

    private void processBatch(List<Task> batch) {
        List<ReportDocument> toIndex = new ArrayList<>();
        List<UUID> toRemove = new ArrayList<>();
        for (Task t : batch) {
            switch (t) {
                case Task.Index idx -> toIndex.add(toDocument(idx.report()));
                case Task.Remove rm -> toRemove.add(rm.reportId());
            }
        }
        if (!toIndex.isEmpty()) {
            try { reportSearchRepository.saveAll(toIndex); }
            catch (Exception e) {
                log.warn("[REPORT INDEX] saveAll reports failed: {}", e.getMessage());
                persistFailures(toIndex.stream().map(ReportDocument::getId).toList(), e.getMessage());
            }
        }
        if (!toRemove.isEmpty()) {
            try { reportSearchRepository.deleteAllById(toRemove); }
            catch (Exception e) {
                log.warn("[REPORT INDEX] deleteAll reports failed: {}", e.getMessage());
                persistFailures(toRemove, e.getMessage());
            }
        }
    }

    private void persistFailures(List<UUID> ids, String errorMessage) {
        try {
            for (UUID id : ids) {
                IndexingFailure f = new IndexingFailure();
                f.setDocumentType(DOC_TYPE);
                f.setDocumentId(id);
                f.setOperation("INDEX");
                f.setErrorMessage(truncate(errorMessage, 500));
                failureRepository.save(f);
            }
        } catch (Exception ex) {
            log.error("[REPORT INDEX] Failed to persist DLQ record: {}", ex.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    // ---------------------------------------------------------------------
    // Document builder
    // ---------------------------------------------------------------------

    private ReportDocument toDocument(Report r) {
        return new ReportDocument(
                r.getId(),
                r.getTargetType() != null ? r.getTargetType().name() : null,
                r.getTargetId() != null ? r.getTargetId().toString() : null,
                r.getReporterId() != null ? r.getReporterId().toString() : null,
                r.getReason() != null ? r.getReason().name() : null,
                r.getTitle(),
                r.getDescription(),
                r.getStatus() != null ? r.getStatus().name() : null,
                null, // targetName — not stored on entity; resolved at query time
                null, // reporterName — not stored on entity; resolved at query time
                r.getScreenshots() != null ? r.getScreenshots().size() : 0,
                r.getCreatedAt(),
                r.getResolvedAt()
        );
    }
}
