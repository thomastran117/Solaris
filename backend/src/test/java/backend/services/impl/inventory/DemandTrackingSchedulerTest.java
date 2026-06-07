package backend.services.impl.inventory;

import backend.repositories.ProductRepository;
import backend.services.intf.inventory.DemandService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DemandTrackingSchedulerTest {

    private ProductRepository productRepository;
    private DemandService demandService;
    private DemandTrackingScheduler scheduler;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        demandService = mock(DemandService.class);
        scheduler = new DemandTrackingScheduler(productRepository, demandService);
    }

    @Test
    void refreshHotProductCaches_noActiveCompanies_noRefreshCalls() {
        when(productRepository.findDistinctCompanyIdsWithPaidOrdersSince(any()))
                .thenReturn(List.of());

        scheduler.refreshHotProductCaches();

        verifyNoInteractions(demandService);
    }

    @Test
    void refreshHotProductCaches_24hRefreshFails_continuesGracefully() {
        UUID companyA = TestIds.uuid(3);
        when(productRepository.findDistinctCompanyIdsWithPaidOrdersSince(any()))
                .thenReturn(List.of(companyA));
        doThrow(new RuntimeException("redis down")).when(demandService).refreshCache(companyA, "24h");

        scheduler.refreshHotProductCaches();

        verify(demandService).refreshCache(companyA, "1h");
        verify(demandService).refreshCache(companyA, "24h");
    }

    @Test
    void refreshHotProductCaches_refreshesBothWindowsAndContinuesAfterFailures() {
        UUID companyA = TestIds.uuid(1);
        UUID companyB = TestIds.uuid(2);
        when(productRepository.findDistinctCompanyIdsWithPaidOrdersSince(any()))
                .thenReturn(List.of(companyA, companyB));
        doThrow(new RuntimeException("boom")).when(demandService).refreshCache(companyB, "1h");

        scheduler.refreshHotProductCaches();

        verify(demandService, times(1)).refreshCache(companyA, "1h");
        verify(demandService, times(1)).refreshCache(companyA, "24h");
        verify(demandService, times(1)).refreshCache(companyB, "1h");
        verify(demandService, times(1)).refreshCache(companyB, "24h");
    }
}
