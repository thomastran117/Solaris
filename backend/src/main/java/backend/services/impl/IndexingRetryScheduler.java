package backend.services.impl;

import backend.kafka.workers.ProductIndexingService;
import backend.kafka.workers.ReviewIndexingService;
import backend.models.core.IndexingFailure;
import backend.models.enums.IndexingFailureStatus;
import backend.repositories.BundleRepository;
import backend.repositories.IndexingFailureRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReviewMediaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class IndexingRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(IndexingRetryScheduler.class);
    private static final int MAX_ATTEMPTS = 5;

    private final IndexingFailureRepository failureRepository;
    private final ProductRepository productRepository;
    private final BundleRepository bundleRepository;
    private final ProductReviewRepository reviewRepository;
    private final ReviewMediaRepository mediaRepository;
    private final ProductIndexingService productIndexingService;
    private final ReviewIndexingService reviewIndexingService;

    public IndexingRetryScheduler(
            IndexingFailureRepository failureRepository,
            ProductRepository productRepository,
            BundleRepository bundleRepository,
            ProductReviewRepository reviewRepository,
            ReviewMediaRepository mediaRepository,
            ProductIndexingService productIndexingService,
            ReviewIndexingService reviewIndexingService) {
        this.failureRepository = failureRepository;
        this.productRepository = productRepository;
        this.bundleRepository = bundleRepository;
        this.reviewRepository = reviewRepository;
        this.mediaRepository = mediaRepository;
        this.productIndexingService = productIndexingService;
        this.reviewIndexingService = reviewIndexingService;
    }

    @Scheduled(fixedDelayString = "${app.elasticsearch.retry.interval-ms:60000}")
    public void retryFailedIndexing() {
        List<IndexingFailure> pending = failureRepository.findAllByStatusAndAttemptsLessThan(
                IndexingFailureStatus.PENDING, MAX_ATTEMPTS);

        if (!pending.isEmpty()) {
            log.info("[SEARCH RETRY] Retrying {} failed indexing record(s)", pending.size());
        }

        for (IndexingFailure f : pending) {
            boolean succeeded = attempt(f);
            f.setAttempts(f.getAttempts() + 1);
            f.setLastAttemptAt(Instant.now());
            if (succeeded) {
                failureRepository.delete(f);
            } else {
                if (f.getAttempts() >= MAX_ATTEMPTS) {
                    f.setStatus(IndexingFailureStatus.EXHAUSTED);
                    log.warn("[SEARCH RETRY] Exhausted retries for {} {} op={}", f.getDocumentType(), f.getDocumentId(), f.getOperation());
                }
                failureRepository.save(f);
            }
        }
    }

    @Scheduled(cron = "${app.elasticsearch.full-reindex.cron:0 0 3 * * SUN}")
    public void weeklyFullReindex() {
        log.info("[SEARCH INDEX] Scheduled full reindex started");
        productIndexingService.reindexAll();
    }

    /**
     * R2-H10 heartbeat: once an hour, count records that have been parked at
     * {@link IndexingFailureStatus#EXHAUSTED} (max-retried, no longer auto-retrying).
     * When this number is non-zero, search results have drifted from the database and
     * an operator needs to investigate (query the table, optionally run
     * {@code productIndexingService.reindexAll()} or re-queue specific docs).
     *
     * <p>This is intentionally just a high-signal log line — wiring to Micrometer or
     * an alerting backend is a separate config change.
     */
    @Scheduled(fixedDelayString = "${app.elasticsearch.exhausted-heartbeat-ms:3600000}")
    public void surfaceExhaustedFailures() {
        long exhausted = failureRepository.countByStatus(IndexingFailureStatus.EXHAUSTED);
        if (exhausted > 0) {
            log.warn("[SEARCH DRIFT] {} indexing failure(s) in EXHAUSTED status — search results may diverge from DB. Investigate indexing_failure table.",
                    exhausted);
        }
    }

    private boolean attempt(IndexingFailure f) {
        try {
            switch (f.getDocumentType()) {
                case "PRODUCT" -> {
                    if ("INDEX".equals(f.getOperation())) {
                        productRepository.findById(f.getDocumentId())
                                .ifPresent(p -> productIndexingService.indexProduct(p, f.getCompanyId()));
                    } else {
                        productIndexingService.removeProduct(f.getDocumentId());
                    }
                }
                case "BUNDLE" -> {
                    if ("INDEX".equals(f.getOperation())) {
                        bundleRepository.findById(f.getDocumentId())
                                .ifPresent(b -> productIndexingService.indexBundle(b));
                    } else {
                        productIndexingService.removeBundle(f.getDocumentId());
                    }
                }
                case "REVIEW" -> {
                    if ("INDEX".equals(f.getOperation())) {
                        reviewRepository.findById(f.getDocumentId()).ifPresent(r -> {
                            boolean hasMedia = mediaRepository.countByReviewId(r.getId()) > 0;
                            reviewIndexingService.indexReview(r, hasMedia);
                        });
                    } else {
                        reviewIndexingService.removeReview(f.getDocumentId());
                    }
                }
                default -> {
                    log.warn("[SEARCH RETRY] Unknown documentType '{}' on failure {} — leaving as-is",
                            f.getDocumentType(), f.getId());
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("[SEARCH RETRY] Attempt failed for {} {} op={}: {}",
                    f.getDocumentType(), f.getDocumentId(), f.getOperation(), e.getMessage());
            return false;
        }
    }
}
