package backend.services.impl.vendors;

import backend.models.core.Company;
import backend.models.core.MarketplaceVendor;
import backend.models.core.VendorSLAPolicy;
import backend.models.enums.SLABreachAction;
import backend.models.enums.VendorStatus;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.VendorAnalyticsRepository;
import backend.repositories.VendorSLABreachRepository;
import backend.repositories.VendorSLAMetricRepository;
import backend.repositories.VendorSLAPolicyRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VendorSLAEvaluationSchedulerTest {

    private VendorSLAPolicyRepository policyRepository;
    private VendorSLAMetricRepository metricRepository;
    private VendorSLABreachRepository breachRepository;
    private VendorAnalyticsRepository analyticsRepository;
    private MarketplaceVendorRepository marketplaceVendorRepository;
    private VendorSLAEvaluationScheduler scheduler;

    @BeforeEach
    void setUp() {
        policyRepository             = mock(VendorSLAPolicyRepository.class);
        metricRepository             = mock(VendorSLAMetricRepository.class);
        breachRepository             = mock(VendorSLABreachRepository.class);
        analyticsRepository          = mock(VendorAnalyticsRepository.class);
        marketplaceVendorRepository  = mock(MarketplaceVendorRepository.class);

        scheduler = new VendorSLAEvaluationScheduler(
                policyRepository,
                metricRepository,
                breachRepository,
                analyticsRepository,
                marketplaceVendorRepository);
    }

    // ─── runEvaluationCycle ───────────────────────────────────────────────────

    @Test
    void runEvaluationCycle_noPolicies_doesNothing() {
        when(policyRepository.findAllByActiveTrue()).thenReturn(List.of());

        scheduler.runEvaluationCycle();

        verifyNoInteractions(marketplaceVendorRepository, analyticsRepository);
    }

    @Test
    void runEvaluationCycle_noOrdersForVendor_skipsEvaluation() {
        UUID marketplaceId = TestIds.uuid(1);
        VendorSLAPolicy policy = makePolicy(marketplaceId, SLABreachAction.WARN);
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(10), marketplaceId);

        when(policyRepository.findAllByActiveTrue()).thenReturn(List.of(policy));
        when(marketplaceVendorRepository.findByMarketplaceIdAndStatus(
                eq(marketplaceId), eq(VendorStatus.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(vendor)));

        // Zero orders — vendor evaluation is skipped
        when(analyticsRepository.vendorTotalOrders(any(), any(), any(), any())).thenReturn(0L);
        when(metricRepository.findByVendorIdAndDate(any(), any())).thenReturn(Optional.empty());

        scheduler.runEvaluationCycle();

        verify(breachRepository, never()).save(any());
    }

    @Test
    void runEvaluationCycle_allRatesWithinThreshold_noBreachRecorded() {
        UUID marketplaceId = TestIds.uuid(1);
        VendorSLAPolicy policy = makePolicy(marketplaceId, SLABreachAction.WARN);
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(10), marketplaceId);

        when(policyRepository.findAllByActiveTrue()).thenReturn(List.of(policy));
        when(marketplaceVendorRepository.findByMarketplaceIdAndStatus(
                eq(marketplaceId), eq(VendorStatus.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(vendor)));

        when(analyticsRepository.vendorTotalOrders(any(), any(), any(), any())).thenReturn(100L);
        when(analyticsRepository.vendorCancelledCount(any(), any(), any(), any())).thenReturn(2L);  // 2% < 5%
        when(analyticsRepository.vendorReturnedCount(any(), any(), any(), any())).thenReturn(1L);   // 1% < 3%
        when(analyticsRepository.vendorShipHours(any(), any(), any(), any(), anyInt())).thenReturn(null);
        when(metricRepository.findByVendorIdAndDate(any(), any())).thenReturn(Optional.empty());
        when(metricRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.runEvaluationCycle();

        verify(breachRepository, never()).save(any());
    }

    @Test
    void runEvaluationCycle_cancellationRateExceedsThreshold_recordsBreach() {
        UUID marketplaceId = TestIds.uuid(1);
        VendorSLAPolicy policy = makePolicy(marketplaceId, SLABreachAction.WARN);
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(10), marketplaceId);

        when(policyRepository.findAllByActiveTrue()).thenReturn(List.of(policy));
        when(marketplaceVendorRepository.findByMarketplaceIdAndStatus(
                eq(marketplaceId), eq(VendorStatus.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(vendor)));

        when(analyticsRepository.vendorTotalOrders(any(), any(), any(), any())).thenReturn(100L);
        when(analyticsRepository.vendorCancelledCount(any(), any(), any(), any())).thenReturn(20L); // 20% > 5%
        when(analyticsRepository.vendorReturnedCount(any(), any(), any(), any())).thenReturn(1L);
        when(analyticsRepository.vendorShipHours(any(), any(), any(), any(), anyInt())).thenReturn(null);
        when(metricRepository.findByVendorIdAndDate(any(), any())).thenReturn(Optional.empty());
        when(metricRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(breachRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.runEvaluationCycle();

        verify(breachRepository, atLeastOnce()).save(any());
    }

    @Test
    void runEvaluationCycle_suspendAction_suspensionsVendor() {
        UUID marketplaceId = TestIds.uuid(1);
        VendorSLAPolicy policy = makePolicy(marketplaceId, SLABreachAction.SUSPEND);
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(10), marketplaceId);

        when(policyRepository.findAllByActiveTrue()).thenReturn(List.of(policy));
        when(marketplaceVendorRepository.findByMarketplaceIdAndStatus(
                eq(marketplaceId), eq(VendorStatus.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(vendor)));

        when(analyticsRepository.vendorTotalOrders(any(), any(), any(), any())).thenReturn(100L);
        when(analyticsRepository.vendorCancelledCount(any(), any(), any(), any())).thenReturn(30L); // 30% > 5%
        when(analyticsRepository.vendorReturnedCount(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorShipHours(any(), any(), any(), any(), anyInt())).thenReturn(null);
        when(metricRepository.findByVendorIdAndDate(any(), any())).thenReturn(Optional.empty());
        when(metricRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(breachRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.runEvaluationCycle();

        assertEquals(VendorStatus.SUSPENDED, vendor.getStatus());
        assertNotNull(vendor.getSuspendedAt());
    }

    @Test
    void runEvaluationCycle_policyEvaluationFails_continuesWithNextPolicy() {
        UUID marketplaceId = TestIds.uuid(1);
        VendorSLAPolicy failingPolicy = makePolicy(marketplaceId, SLABreachAction.WARN);
        VendorSLAPolicy goodPolicy = makePolicy(TestIds.uuid(2), SLABreachAction.WARN);
        goodPolicy.setId(TestIds.uuid(99));

        when(policyRepository.findAllByActiveTrue()).thenReturn(List.of(failingPolicy, goodPolicy));
        // First policy throws during vendor lookup
        when(marketplaceVendorRepository.findByMarketplaceIdAndStatus(
                eq(marketplaceId), any(), any(Pageable.class)))
                .thenThrow(new RuntimeException("DB timeout"));
        // Second policy has no vendors
        when(marketplaceVendorRepository.findByMarketplaceIdAndStatus(
                eq(TestIds.uuid(2)), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertDoesNotThrow(() -> scheduler.runEvaluationCycle());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private VendorSLAPolicy makePolicy(UUID marketplaceId, SLABreachAction action) {
        VendorSLAPolicy policy = new VendorSLAPolicy();
        policy.setId(TestIds.uuid(5));
        policy.setMarketplaceId(marketplaceId);
        policy.setBreachAction(action);
        policy.setMaxCancellationRate(0.05);
        policy.setMaxRefundRate(0.03);
        policy.setMaxLateShipmentRate(0.10);
        policy.setTargetShipHours(48);
        policy.setEvaluationWindowDays(30);
        policy.setActive(true);
        return policy;
    }

    private MarketplaceVendor makeVendor(UUID id, UUID marketplaceId) {
        Company marketplace = new Company();
        marketplace.setId(marketplaceId);
        MarketplaceVendor vendor = new MarketplaceVendor();
        vendor.setId(id);
        vendor.setMarketplace(marketplace);
        vendor.setStatus(VendorStatus.APPROVED);
        return vendor;
    }
}
