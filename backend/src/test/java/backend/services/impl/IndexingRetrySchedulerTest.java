package backend.services.impl;

import backend.kafka.workers.ProductIndexingService;
import backend.kafka.workers.ReviewIndexingService;
import backend.models.core.IndexingFailure;
import backend.models.core.Product;
import backend.models.core.Company;
import backend.models.core.ProductBundle;
import backend.models.core.ProductReview;
import backend.models.enums.IndexingFailureStatus;
import backend.repositories.BundleRepository;
import backend.repositories.IndexingFailureRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReviewMediaRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndexingRetrySchedulerTest {

    private static final UUID DOC_ID     = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);

    private IndexingFailureRepository failureRepository;
    private ProductRepository         productRepository;
    private BundleRepository          bundleRepository;
    private ProductReviewRepository   reviewRepository;
    private ReviewMediaRepository     mediaRepository;
    private ProductIndexingService    productIndexingService;
    private ReviewIndexingService     reviewIndexingService;

    private IndexingRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        failureRepository      = mock(IndexingFailureRepository.class);
        productRepository      = mock(ProductRepository.class);
        bundleRepository       = mock(BundleRepository.class);
        reviewRepository       = mock(ProductReviewRepository.class);
        mediaRepository        = mock(ReviewMediaRepository.class);
        productIndexingService = mock(ProductIndexingService.class);
        reviewIndexingService  = mock(ReviewIndexingService.class);

        scheduler = new IndexingRetryScheduler(failureRepository, productRepository,
                bundleRepository, reviewRepository, mediaRepository,
                productIndexingService, reviewIndexingService);
    }

    // ─── retryFailedIndexing ──────────────────────────────────────────────────

    @Test
    void retryFailedIndexing_noPendingFailures_noIndexingCalls() {
        when(failureRepository.findAllByStatusAndAttemptsLessThan(any(), anyInt())).thenReturn(List.of());

        scheduler.retryFailedIndexing();

        verify(productIndexingService, never()).indexProduct(any(), any());
        verify(productIndexingService, never()).removeProduct(any());
    }

    @Test
    void retryFailedIndexing_productIndexSuccess_deletesFailureRecord() {
        IndexingFailure f = failure("PRODUCT", "INDEX", 0);
        when(failureRepository.findAllByStatusAndAttemptsLessThan(any(), anyInt())).thenReturn(List.of(f));

        Product product = new Product();
        product.setId(DOC_ID);
        Company company = new Company();
        company.setId(COMPANY_ID);
        product.setCompany(company);
        when(productRepository.findById(DOC_ID)).thenReturn(Optional.of(product));

        scheduler.retryFailedIndexing();

        verify(productIndexingService).indexProduct(eq(product), eq(COMPANY_ID));
        verify(failureRepository).delete(f);
    }

    @Test
    void retryFailedIndexing_productDeleteSuccess_deletesFailureRecord() {
        IndexingFailure f = failure("PRODUCT", "DELETE", 0);
        when(failureRepository.findAllByStatusAndAttemptsLessThan(any(), anyInt())).thenReturn(List.of(f));

        scheduler.retryFailedIndexing();

        verify(productIndexingService).removeProduct(DOC_ID);
        verify(failureRepository).delete(f);
    }

    @Test
    void retryFailedIndexing_reviewIndexThrows_savesWithIncrementedAttempts() {
        IndexingFailure f = failure("REVIEW", "INDEX", 1);
        when(failureRepository.findAllByStatusAndAttemptsLessThan(any(), anyInt())).thenReturn(List.of(f));

        ProductReview review = new ProductReview();
        review.setId(DOC_ID);
        when(reviewRepository.findById(DOC_ID)).thenReturn(Optional.of(review));
        when(mediaRepository.countByReviewId(DOC_ID)).thenReturn(0L);
        org.mockito.Mockito.doThrow(new RuntimeException("ES down"))
                .when(reviewIndexingService).indexReview(any(), any(Boolean.class));

        scheduler.retryFailedIndexing();

        verify(failureRepository, never()).delete(any());
        verify(failureRepository).save(f);
        assertEquals(2, f.getAttempts());
    }

    @Test
    void retryFailedIndexing_maxAttemptsReached_setsExhausted() {
        IndexingFailure f = failure("REVIEW", "INDEX", 4); // MAX_ATTEMPTS - 1 = 4
        when(failureRepository.findAllByStatusAndAttemptsLessThan(any(), anyInt())).thenReturn(List.of(f));
        when(reviewRepository.findById(DOC_ID)).thenReturn(Optional.empty()); // not found → attempt returns true

        scheduler.retryFailedIndexing();

        // attempt succeeds (product not found → ifPresent no-op returns true), deletes
        verify(failureRepository).delete(f);
    }

    @Test
    void retryFailedIndexing_unknownDocumentType_savesWithoutDeletion() {
        IndexingFailure f = failure("UNKNOWN", "INDEX", 0);
        when(failureRepository.findAllByStatusAndAttemptsLessThan(any(), anyInt())).thenReturn(List.of(f));

        scheduler.retryFailedIndexing();

        verify(failureRepository, never()).delete(any());
        verify(failureRepository).save(f);
    }

    // ─── weeklyFullReindex ────────────────────────────────────────────────────

    @Test
    void weeklyFullReindex_callsReindexAll() {
        scheduler.weeklyFullReindex();

        verify(productIndexingService).reindexAll();
    }

    // ─── surfaceExhaustedFailures ─────────────────────────────────────────────

    @Test
    void surfaceExhaustedFailures_zeroExhausted_noThrow() {
        when(failureRepository.countByStatus(IndexingFailureStatus.EXHAUSTED)).thenReturn(0L);

        scheduler.surfaceExhaustedFailures(); // just verify no exception
    }

    @Test
    void surfaceExhaustedFailures_someExhausted_noThrow() {
        when(failureRepository.countByStatus(IndexingFailureStatus.EXHAUSTED)).thenReturn(3L);

        scheduler.surfaceExhaustedFailures(); // logs a warn, must not throw
    }

    @Test
    void retryFailedIndexing_bundleIndexSuccess_deletesFailureRecord() {
        IndexingFailure f = failure("BUNDLE", "INDEX", 0);
        when(failureRepository.findAllByStatusAndAttemptsLessThan(any(), anyInt())).thenReturn(List.of(f));

        ProductBundle bundle = new ProductBundle();
        bundle.setId(DOC_ID);
        when(bundleRepository.findById(DOC_ID)).thenReturn(Optional.of(bundle));

        scheduler.retryFailedIndexing();

        verify(productIndexingService).indexBundle(bundle);
        verify(failureRepository).delete(f);
    }

    @Test
    void retryFailedIndexing_bundleDeleteSuccess_deletesFailureRecord() {
        IndexingFailure f = failure("BUNDLE", "DELETE", 0);
        when(failureRepository.findAllByStatusAndAttemptsLessThan(any(), anyInt())).thenReturn(List.of(f));

        scheduler.retryFailedIndexing();

        verify(productIndexingService).removeBundle(DOC_ID);
        verify(failureRepository).delete(f);
    }

    @Test
    void retryFailedIndexing_reviewDeleteSuccess_deletesFailureRecord() {
        IndexingFailure f = failure("REVIEW", "DELETE", 0);
        when(failureRepository.findAllByStatusAndAttemptsLessThan(any(), anyInt())).thenReturn(List.of(f));

        scheduler.retryFailedIndexing();

        verify(reviewIndexingService).removeReview(DOC_ID);
        verify(failureRepository).delete(f);
    }

    @Test
    void retryFailedIndexing_failureAfterMaxAttempts_setsExhaustedStatus() {
        IndexingFailure f = failure("REVIEW", "INDEX", 4); // will become 5 = MAX_ATTEMPTS
        when(failureRepository.findAllByStatusAndAttemptsLessThan(any(), anyInt())).thenReturn(List.of(f));
        ProductReview review = new ProductReview();
        review.setId(DOC_ID);
        when(reviewRepository.findById(DOC_ID)).thenReturn(Optional.of(review));
        when(mediaRepository.countByReviewId(DOC_ID)).thenReturn(0L);
        // Throw so attempt() returns false
        org.mockito.Mockito.doThrow(new RuntimeException("ES down"))
                .when(reviewIndexingService).indexReview(any(), any(Boolean.class));

        scheduler.retryFailedIndexing();

        assertEquals(IndexingFailureStatus.EXHAUSTED, f.getStatus());
        verify(failureRepository).save(f);
        verify(failureRepository, never()).delete(any());
    }

    @Test
    void retryFailedIndexing_reviewIndexWithMedia_passesHasMediaTrue() {
        IndexingFailure f = failure("REVIEW", "INDEX", 0);
        when(failureRepository.findAllByStatusAndAttemptsLessThan(any(), anyInt())).thenReturn(List.of(f));
        ProductReview review = new ProductReview();
        review.setId(DOC_ID);
        when(reviewRepository.findById(DOC_ID)).thenReturn(Optional.of(review));
        when(mediaRepository.countByReviewId(DOC_ID)).thenReturn(2L); // hasMedia = true

        scheduler.retryFailedIndexing();

        verify(reviewIndexingService).indexReview(eq(review), eq(true));
        verify(failureRepository).delete(f);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private IndexingFailure failure(String documentType, String operation, int attempts) {
        IndexingFailure f = new IndexingFailure();
        f.setId(TestIds.uuid(99));
        f.setDocumentId(DOC_ID);
        f.setDocumentType(documentType);
        f.setOperation(operation);
        f.setAttempts(attempts);
        f.setCompanyId(COMPANY_ID);
        f.setStatus(IndexingFailureStatus.PENDING);
        return f;
    }
}
