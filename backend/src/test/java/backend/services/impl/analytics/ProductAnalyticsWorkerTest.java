package backend.services.impl.analytics;

import backend.repositories.ProductRepository;
import backend.services.intf.CacheService;
import backend.services.intf.analytics.CompanyAnalyticsService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductAnalyticsWorkerTest {

    private static final UUID COMPANY_1 = TestIds.uuid(1);
    private static final UUID COMPANY_2 = TestIds.uuid(2);

    private CompanyAnalyticsService companyAnalyticsService;
    private ProductRepository       productRepository;
    private CacheService            cacheService;

    private ProductAnalyticsWorker worker;

    @BeforeEach
    void setUp() {
        companyAnalyticsService = mock(CompanyAnalyticsService.class);
        productRepository       = mock(ProductRepository.class);
        cacheService            = mock(CacheService.class);

        worker = new ProductAnalyticsWorker(companyAnalyticsService, productRepository, cacheService);
    }

    @Test
    void refreshProductAnalytics_lockNotAcquired_skipsPrecompute() {
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(false);

        worker.refreshProductAnalytics();

        verify(companyAnalyticsService, never()).precomputeAll(any());
    }

    @Test
    void refreshProductAnalytics_lockAcquired_noActiveCompanies_skipsPrecompute() {
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(productRepository.findDistinctCompanyIdsWithPaidOrdersSince(any(Instant.class))).thenReturn(List.of());

        worker.refreshProductAnalytics();

        verify(companyAnalyticsService, never()).precomputeAll(any());
        verify(cacheService).unlock(anyString(), anyString());
    }

    @Test
    void refreshProductAnalytics_twoCompaniesOneThrows_otherStillPrecomputed() {
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(productRepository.findDistinctCompanyIdsWithPaidOrdersSince(any(Instant.class)))
                .thenReturn(List.of(COMPANY_1, COMPANY_2));
        doThrow(new RuntimeException("ES down")).when(companyAnalyticsService).precomputeAll(COMPANY_1);

        worker.refreshProductAnalytics();

        verify(companyAnalyticsService).precomputeAll(COMPANY_1);
        verify(companyAnalyticsService).precomputeAll(COMPANY_2);
        verify(cacheService).unlock(anyString(), anyString()); // unlock always called in finally
    }

    @Test
    void refreshProductAnalytics_lockAcquisitionThrows_proceedsWithoutLock() {
        when(cacheService.tryLock(anyString(), anyString(), anyLong()))
                .thenThrow(new RuntimeException("Redis down"));
        when(productRepository.findDistinctCompanyIdsWithPaidOrdersSince(any(Instant.class)))
                .thenReturn(List.of(COMPANY_1));

        worker.refreshProductAnalytics(); // must not throw

        verify(companyAnalyticsService).precomputeAll(COMPANY_1);
    }

    @Test
    void refreshProductAnalytics_unlockThrows_exceptionSwallowed() {
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(productRepository.findDistinctCompanyIdsWithPaidOrdersSince(any(Instant.class)))
                .thenReturn(List.of());
        doThrow(new RuntimeException("Redis down")).when(cacheService).unlock(anyString(), anyString());

        worker.refreshProductAnalytics(); // must not throw
    }
}
