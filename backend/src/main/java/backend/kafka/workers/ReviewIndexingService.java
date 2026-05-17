package backend.kafka.workers;

import backend.configurations.environment.EnvironmentSetting;
import backend.documents.ReviewDocument;
import backend.models.core.IndexingFailure;
import backend.models.core.ProductReview;
import backend.models.core.User;
import backend.repositories.IndexingFailureRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReviewMediaRepository;
import backend.repositories.search.ReviewSearchRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async Elasticsearch indexer for {@link ReviewDocument}. Mirrors {@link ProductIndexingService}:
 * a bounded queue feeds a fixed pool of workers that batch saveAll/deleteAllById calls.
 * Failures are parked in {@code indexing_failures} (documentType = "REVIEW") so the existing
 * {@link backend.services.impl.IndexingRetryScheduler} can replay them.
 */
@Component
public class ReviewIndexingService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReviewIndexingService.class);
    static final String DOC_TYPE = "REVIEW";

    sealed interface Task permits Task.Index, Task.Remove {
        record Index(ProductReview review, boolean hasMedia) implements Task {}
        record Remove(long reviewId) implements Task {}
    }

    private final ReviewSearchRepository reviewSearchRepository;
    private final ProductReviewRepository reviewRepository;
    private final ReviewMediaRepository mediaRepository;
    private final IndexingFailureRepository failureRepository;
    private final IndexVersionManager indexVersionManager;
    private final EnvironmentSetting env;

    private volatile LinkedBlockingQueue<Task> taskQueue;
    private volatile ExecutorService workerPool;

    public ReviewIndexingService(
            ReviewSearchRepository reviewSearchRepository,
            ProductReviewRepository reviewRepository,
            ReviewMediaRepository mediaRepository,
            IndexingFailureRepository failureRepository,
            IndexVersionManager indexVersionManager,
            EnvironmentSetting env) {
        this.reviewSearchRepository = reviewSearchRepository;
        this.reviewRepository = reviewRepository;
        this.mediaRepository = mediaRepository;
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
            Thread t = new Thread(r, "review-index-worker-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < Math.max(1, cfg.getWorkerCount()); i++) {
            workerPool.submit(this::workerLoop);
        }
        log.info("[REVIEW INDEX] Started {} worker(s)", cfg.getWorkerCount());
    }

    @PreDestroy
    void shutdown() {
        log.info("[REVIEW INDEX] Shutting down — pending tasks: {}", taskQueue.size());
        workerPool.shutdownNow();
        try {
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("[REVIEW INDEX] Workers did not terminate within 5s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    public void indexReview(ProductReview review, boolean hasMedia) {
        submit(new Task.Index(review, hasMedia));
    }

    public void removeReview(long reviewId) {
        submit(new Task.Remove(reviewId));
    }

    public void reindexAll() {
        List<ProductReview> all = reviewRepository.findAll();
        if (all.isEmpty()) {
            log.info("[REVIEW INDEX] reindexAll — no reviews");
            return;
        }
        Set<Long> hasMedia = mediaReviewIds(all);
        for (ProductReview r : all) submit(new Task.Index(r, hasMedia.contains(r.getId())));
        log.info("[REVIEW INDEX] Queued {} reviews for full reindex", all.size());
    }

    private void submit(Task task) {
        if (!taskQueue.offer(task)) {
            log.warn("[REVIEW INDEX] Queue full — dropping task: {}", task);
        }
    }

    // ---------------------------------------------------------------------
    // Startup — create index, queue initial population if empty
    // ---------------------------------------------------------------------

    @Override
    public void run(ApplicationArguments args) {
        indexVersionManager.ensureIndexExists("reviews");
        try {
            if (reviewSearchRepository.count() == 0) {
                reindexAll();
                log.info("[REVIEW INDEX] Queued existing reviews for initial indexing");
            }
        } catch (Exception e) {
            log.warn("[REVIEW INDEX] Startup reindex skipped: {}", e.getMessage());
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
                log.warn("[REVIEW INDEX] Worker error: {}", e.getMessage());
            }
        }
        List<Task> remaining = new ArrayList<>();
        taskQueue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            try { processBatch(remaining); }
            catch (Exception e) { log.warn("[REVIEW INDEX] Shutdown drain error: {}", e.getMessage()); }
        }
    }

    private void processBatch(List<Task> batch) {
        List<ReviewDocument> toIndex = new ArrayList<>();
        List<Long> toRemove = new ArrayList<>();
        for (Task t : batch) {
            switch (t) {
                case Task.Index idx -> toIndex.add(toDocument(idx.review(), idx.hasMedia()));
                case Task.Remove rm -> toRemove.add(rm.reviewId());
            }
        }
        if (!toIndex.isEmpty()) {
            try { reviewSearchRepository.saveAll(toIndex); }
            catch (Exception e) {
                log.warn("[REVIEW INDEX] saveAll reviews failed: {}", e.getMessage());
                persistFailures("INDEX", toIndex.stream().map(d -> new long[]{d.getId(), d.getCompanyId() == null ? 0L : d.getCompanyId()}).toList(), e.getMessage());
            }
        }
        if (!toRemove.isEmpty()) {
            try { reviewSearchRepository.deleteAllById(toRemove); }
            catch (Exception e) {
                log.warn("[REVIEW INDEX] deleteAll reviews failed: {}", e.getMessage());
                persistRemoveFailures(toRemove, e.getMessage());
            }
        }
    }

    private void persistFailures(String operation, List<long[]> idPairs, String errorMessage) {
        try {
            for (long[] pair : idPairs) {
                IndexingFailure f = new IndexingFailure();
                f.setDocumentType(DOC_TYPE);
                f.setDocumentId(pair[0]);
                f.setCompanyId(pair[1] == 0L ? null : pair[1]);
                f.setOperation(operation);
                f.setErrorMessage(truncate(errorMessage, 500));
                failureRepository.save(f);
            }
        } catch (Exception ex) {
            log.error("[REVIEW INDEX] Failed to persist DLQ record: {}", ex.getMessage());
        }
    }

    private void persistRemoveFailures(List<Long> ids, String errorMessage) {
        try {
            for (Long id : ids) {
                IndexingFailure f = new IndexingFailure();
                f.setDocumentType(DOC_TYPE);
                f.setDocumentId(id);
                f.setOperation("REMOVE");
                f.setErrorMessage(truncate(errorMessage, 500));
                failureRepository.save(f);
            }
        } catch (Exception ex) {
            log.error("[REVIEW INDEX] Failed to persist DLQ record: {}", ex.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    // ---------------------------------------------------------------------
    // Document builder
    // ---------------------------------------------------------------------

    private ReviewDocument toDocument(ProductReview r, boolean hasMedia) {
        Long companyId = null;
        Long marketplaceId = null;
        try {
            if (r.getProduct() != null) {
                if (r.getProduct().getCompany() != null) companyId = r.getProduct().getCompany().getId();
                marketplaceId = r.getProduct().getMarketplaceId();
            }
        } catch (Exception ignored) {}

        String reviewerName = "";
        Long reviewerId = null;
        try {
            User u = r.getReviewer();
            if (u != null) {
                reviewerId = u.getId();
                reviewerName = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                        + (u.getLastName() == null ? "" : u.getLastName())).trim();
            }
        } catch (Exception ignored) {}

        return new ReviewDocument(
                r.getId(),
                r.getProduct() != null ? r.getProduct().getId() : null,
                companyId,
                marketplaceId,
                reviewerId,
                reviewerName,
                r.getTitle(),
                r.getBody(),
                r.getRating(),
                r.isVerifiedPurchase(),
                hasMedia,
                r.getHelpfulCount(),
                r.getStatus() != null ? r.getStatus().name() : null,
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }

    private Set<Long> mediaReviewIds(List<ProductReview> reviews) {
        if (reviews.isEmpty()) return Set.of();
        List<Long> ids = reviews.stream().map(ProductReview::getId).toList();
        Set<Long> withMedia = new HashSet<>();
        for (var m : mediaRepository.findByReviewIdInOrderByReviewIdAscPositionAsc(ids)) {
            withMedia.add(m.getReviewId());
        }
        return withMedia;
    }
}
