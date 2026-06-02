package backend.kafka.workers;

import backend.models.core.Product;
import backend.repositories.ProductRepository;
import backend.repositories.ProductSimilarityRepository;
import backend.services.impl.products.ProductSimilarityService;
import backend.testutil.TestIds;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSimilarityWorkerTest {

    private static final UUID COMPANY_ID  = TestIds.uuid(1);
    private static final UUID PRODUCT_ID  = TestIds.uuid(2);

    private ProductSimilarityService    similarityService;
    private ProductRepository           productRepository;
    private ProductSimilarityRepository similarityRepository;
    private MeterRegistry               meterRegistry;

    private ProductSimilarityWorker worker;

    @BeforeEach
    void setUp() {
        similarityService    = mock(ProductSimilarityService.class);
        productRepository    = mock(ProductRepository.class);
        similarityRepository = mock(ProductSimilarityRepository.class);
        meterRegistry        = mock(MeterRegistry.class);

        Counter counter = mock(Counter.class);
        when(meterRegistry.counter(any(String.class))).thenReturn(counter);

        worker = new ProductSimilarityWorker(
                similarityService, productRepository, similarityRepository, meterRegistry);
    }

    @Test
    void recomputeAll_noCompanies_doesNotProcess() {
        when(productRepository.findDistinctCompanyIdsWithActiveProducts()).thenReturn(List.of());

        worker.recomputeAll();

        verify(productRepository, never()).findActiveByCompanyId(any(), any());
    }

    @Test
    void recomputeAll_productNotFresh_callsRecompute() {
        when(productRepository.findDistinctCompanyIdsWithActiveProducts()).thenReturn(List.of(COMPANY_ID));

        Product product = new Product();
        product.setId(PRODUCT_ID);
        when(productRepository.findActiveByCompanyId(eq(COMPANY_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)))
                .thenReturn(new PageImpl<>(List.of())); // empty on second call to end loop

        when(similarityRepository.findSourceIdsWithFreshRows(anyList(), any(Instant.class)))
                .thenReturn(List.of()); // no fresh rows — product needs recompute

        worker.recomputeAll();

        verify(similarityService).recompute(PRODUCT_ID);
    }

    @Test
    void recomputeAll_productAlreadyFresh_skipsRecompute() {
        when(productRepository.findDistinctCompanyIdsWithActiveProducts()).thenReturn(List.of(COMPANY_ID));

        Product product = new Product();
        product.setId(PRODUCT_ID);
        when(productRepository.findActiveByCompanyId(eq(COMPANY_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)))
                .thenReturn(new PageImpl<>(List.of()));

        when(similarityRepository.findSourceIdsWithFreshRows(anyList(), any(Instant.class)))
                .thenReturn(List.of(PRODUCT_ID)); // already fresh

        worker.recomputeAll();

        verify(similarityService, never()).recompute(PRODUCT_ID);
    }

    @Test
    void recomputeAll_recomputeThrows_continuesWithoutPropagating() {
        when(productRepository.findDistinctCompanyIdsWithActiveProducts()).thenReturn(List.of(COMPANY_ID));

        Product product = new Product();
        product.setId(PRODUCT_ID);
        when(productRepository.findActiveByCompanyId(eq(COMPANY_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)))
                .thenReturn(new PageImpl<>(List.of()));

        when(similarityRepository.findSourceIdsWithFreshRows(anyList(), any(Instant.class)))
                .thenReturn(List.of());
        doThrow(new RuntimeException("model error")).when(similarityService).recompute(PRODUCT_ID);

        assertDoesNotThrow(() -> worker.recomputeAll());
    }

    @Test
    void recomputeAll_companyThrows_otherCompaniesStillProcessed() {
        UUID company2 = TestIds.uuid(3);
        when(productRepository.findDistinctCompanyIdsWithActiveProducts()).thenReturn(List.of(COMPANY_ID, company2));

        // company1 — throws during processing
        when(productRepository.findActiveByCompanyId(eq(COMPANY_ID), any(Pageable.class)))
                .thenThrow(new RuntimeException("DB error"));
        // company2 — normal empty batch
        when(productRepository.findActiveByCompanyId(eq(company2), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertDoesNotThrow(() -> worker.recomputeAll());
    }
}
