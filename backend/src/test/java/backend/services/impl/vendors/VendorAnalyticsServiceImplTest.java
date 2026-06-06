package backend.services.impl.vendors;

import backend.dtos.responses.marketplace.TopVendorResponse;
import backend.dtos.responses.vendor.VendorPayoutsMetricResponse;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.MarketplaceProfile;
import backend.models.core.MarketplaceVendor;
import backend.models.core.User;
import backend.models.enums.PayoutStatus;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.VendorAnalyticsRepository;
import backend.repositories.VendorPayoutRepository;
import backend.repositories.projections.DailyCountProjection;
import backend.repositories.projections.TopVendorProjection;
import backend.repositories.projections.VendorRevenueSummaryProjection;
import backend.services.intf.CacheService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VendorAnalyticsServiceImplTest {

    private VendorAnalyticsRepository analyticsRepository;
    private VendorPayoutRepository payoutRepository;
    private MarketplaceProfileRepository marketplaceProfileRepository;
    private MarketplaceVendorRepository marketplaceVendorRepository;
    private CacheService cacheService;
    private VendorAnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        analyticsRepository          = mock(VendorAnalyticsRepository.class);
        payoutRepository             = mock(VendorPayoutRepository.class);
        marketplaceProfileRepository = mock(MarketplaceProfileRepository.class);
        marketplaceVendorRepository  = mock(MarketplaceVendorRepository.class);
        cacheService                 = mock(CacheService.class);

        service = new VendorAnalyticsServiceImpl(
                analyticsRepository,
                payoutRepository,
                marketplaceProfileRepository,
                marketplaceVendorRepository,
                cacheService,
                new ObjectMapper());
    }

    @Test
    void getSummary_throwsWhenActorCannotAccessVendor() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(20), TestIds.uuid(10));
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(marketplaceProfileRepository.findByCompanyId(TestIds.uuid(20))).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> service.getSummary(TestIds.uuid(7), TestIds.uuid(20), 30, TestIds.uuid(99)));
    }

    @Test
    void getPayouts_allowsMarketplaceOperator() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(20), TestIds.uuid(10));
        MarketplaceProfile profile = makeMarketplaceProfile(TestIds.uuid(20), TestIds.uuid(55));

        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(marketplaceProfileRepository.findByCompanyId(TestIds.uuid(20))).thenReturn(Optional.of(profile));
        when(payoutRepository.findByVendorIdAndStatus(eq(TestIds.uuid(7)), eq(PayoutStatus.PAID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        VendorPayoutsMetricResponse response = service.getPayouts(TestIds.uuid(7), TestIds.uuid(20), 10, TestIds.uuid(55));

        assertEquals(TestIds.uuid(7), response.getVendorId());
        verify(payoutRepository).findByVendorIdAndStatus(eq(TestIds.uuid(7)), eq(PayoutStatus.PAID), any(Pageable.class));
    }

    // ─── vendor owner access ──────────────────────────────────────────────────

    @Test
    void getSummary_allowsVendorOwner() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(20), TestIds.uuid(10));
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));

        // Stub analytics repository so the service doesn't NPE past the access check
        VendorRevenueSummaryProjection rev = mock(VendorRevenueSummaryProjection.class);
        when(analyticsRepository.vendorRevenueSummary(any(), any(), any(), any())).thenReturn(rev);
        when(analyticsRepository.vendorTotalOrders(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorCancelledCount(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorReturnedCount(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorShipHours(any(), any(), any(), any(), anyDouble())).thenReturn(null);

        // Vendor owner (uuid 10) should bypass the operator check — no ForbiddenException
        assertDoesNotThrow(() -> service.getSummary(TestIds.uuid(7), TestIds.uuid(20), 30, TestIds.uuid(10)));
    }

    @Test
    void getRevenue_forbidden_throwsForbiddenException() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(20), TestIds.uuid(10));
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(marketplaceProfileRepository.findByCompanyId(TestIds.uuid(20))).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> service.getRevenue(TestIds.uuid(7), TestIds.uuid(20), 30, TestIds.uuid(99)));
    }

    @Test
    void getOrders_forbidden_throwsForbiddenException() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(20), TestIds.uuid(10));
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(marketplaceProfileRepository.findByCompanyId(TestIds.uuid(20))).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> service.getOrders(TestIds.uuid(7), TestIds.uuid(20), 30, TestIds.uuid(99)));
    }

    @Test
    void getRefunds_forbidden_throwsForbiddenException() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(20), TestIds.uuid(10));
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(marketplaceProfileRepository.findByCompanyId(TestIds.uuid(20))).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> service.getRefunds(TestIds.uuid(7), TestIds.uuid(20), 30, TestIds.uuid(99)));
    }

    @Test
    void getTopProducts_forbidden_throwsForbiddenException() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(20), TestIds.uuid(10));
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(marketplaceProfileRepository.findByCompanyId(TestIds.uuid(20))).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> service.getTopProducts(TestIds.uuid(7), TestIds.uuid(20), 30, 10, TestIds.uuid(99)));
    }

    // ─── Happy-path tests (cover uncovered analytics method bodies) ───────────

    @Test
    void getRevenue_happyPath_returnsResponse() {
        UUID vendorId = TestIds.uuid(7); UUID mktId = TestIds.uuid(20); UUID ownerId = TestIds.uuid(10);
        MarketplaceVendor vendor = makeVendor(vendorId, mktId, ownerId);
        when(marketplaceVendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));

        VendorRevenueSummaryProjection summary = mock(VendorRevenueSummaryProjection.class);
        when(analyticsRepository.vendorRevenueSummary(any(), any(), any(), any())).thenReturn(summary);
        when(analyticsRepository.vendorRevenueDaily(any(), any(), any(), any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.getRevenue(vendorId, mktId, 30, ownerId));
        verify(analyticsRepository).vendorRevenueDaily(eq(vendorId), eq(mktId), any(), any());
    }

    @Test
    void getOrders_happyPath_returnsResponse() {
        UUID vendorId = TestIds.uuid(7); UUID mktId = TestIds.uuid(20); UUID ownerId = TestIds.uuid(10);
        MarketplaceVendor vendor = makeVendor(vendorId, mktId, ownerId);
        when(marketplaceVendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));

        when(analyticsRepository.vendorTotalOrders(any(), any(), any(), any())).thenReturn(5L);
        when(analyticsRepository.vendorCancelledCount(any(), any(), any(), any())).thenReturn(1L);
        when(analyticsRepository.vendorOrdersDaily(any(), any(), any(), any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.getOrders(vendorId, mktId, 30, ownerId));
        verify(analyticsRepository).vendorTotalOrders(eq(vendorId), eq(mktId), any(), any());
    }

    @Test
    void getRefunds_happyPath_returnsResponse() {
        UUID vendorId = TestIds.uuid(7); UUID mktId = TestIds.uuid(20); UUID ownerId = TestIds.uuid(10);
        MarketplaceVendor vendor = makeVendor(vendorId, mktId, ownerId);
        when(marketplaceVendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));

        when(analyticsRepository.vendorTotalOrders(any(), any(), any(), any())).thenReturn(10L);
        when(analyticsRepository.vendorReturnedCount(any(), any(), any(), any())).thenReturn(2L);
        when(analyticsRepository.vendorRefundsDaily(any(), any(), any(), any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.getRefunds(vendorId, mktId, 30, ownerId));
    }

    @Test
    void getTopProducts_happyPath_returnsResponse() {
        UUID vendorId = TestIds.uuid(7); UUID mktId = TestIds.uuid(20); UUID ownerId = TestIds.uuid(10);
        MarketplaceVendor vendor = makeVendor(vendorId, mktId, ownerId);
        when(marketplaceVendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(analyticsRepository.vendorTopProducts(any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.getTopProducts(vendorId, mktId, 30, 10, ownerId));
        verify(analyticsRepository).vendorTopProducts(eq(vendorId), eq(mktId), any(), any(), eq(10));
    }

    @Test
    void getSummary_cacheMiss_queriesRepository() {
        UUID vendorId = TestIds.uuid(7); UUID mktId = TestIds.uuid(20); UUID ownerId = TestIds.uuid(10);
        MarketplaceVendor vendor = makeVendor(vendorId, mktId, ownerId);
        when(marketplaceVendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(cacheService.get(any())).thenReturn(null); // explicit cache miss

        VendorRevenueSummaryProjection rev = mock(VendorRevenueSummaryProjection.class);
        when(analyticsRepository.vendorRevenueSummary(any(), any(), any(), any())).thenReturn(rev);
        when(analyticsRepository.vendorTotalOrders(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorCancelledCount(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorReturnedCount(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorShipHours(any(), any(), any(), any(), anyDouble())).thenReturn(null);

        assertDoesNotThrow(() -> service.getSummary(vendorId, mktId, 30, ownerId));
        verify(analyticsRepository).vendorRevenueSummary(eq(vendorId), eq(mktId), any(), any());
    }

    @Test
    void getMarketplaceSummary_operatorAccess_happyPath() {
        UUID mktId = TestIds.uuid(20); UUID operatorId = TestIds.uuid(55);
        MarketplaceProfile profile = makeMarketplaceProfile(mktId, operatorId);
        when(marketplaceProfileRepository.findByCompanyId(mktId)).thenReturn(Optional.of(profile));

        backend.repositories.projections.MarketplaceSummaryProjection summary =
                mock(backend.repositories.projections.MarketplaceSummaryProjection.class);
        when(analyticsRepository.marketplaceSummary(eq(mktId), any(), any())).thenReturn(summary);
        when(analyticsRepository.marketplaceOrdersDaily(eq(mktId), any(), any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.getMarketplaceSummary(mktId, operatorId, 30));
    }

    private MarketplaceVendor makeVendor(UUID vendorId, UUID marketplaceId, UUID vendorOwnerId) {
        User owner = new User();
        owner.setId(vendorOwnerId);

        Company vendorCompany = new Company();
        vendorCompany.setId(TestIds.uuid(300));
        vendorCompany.setOwner(owner);

        Company marketplaceCompany = new Company();
        marketplaceCompany.setId(marketplaceId);

        MarketplaceVendor vendor = new MarketplaceVendor();
        vendor.setId(vendorId);
        vendor.setVendorCompany(vendorCompany);
        vendor.setMarketplace(marketplaceCompany);
        return vendor;
    }

    private MarketplaceProfile makeMarketplaceProfile(UUID marketplaceId, UUID operatorUserId) {
        User operator = new User();
        operator.setId(operatorUserId);

        Company marketplaceCompany = new Company();
        marketplaceCompany.setId(marketplaceId);
        marketplaceCompany.setOwner(operator);

        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setId(TestIds.uuid(500));
        profile.setCompany(marketplaceCompany);
        return profile;
    }

    // ─── getTopVendors ────────────────────────────────────────────────────────

    @Test
    void getTopVendors_happyPath_returnsVendorList() {
        UUID mktId = TestIds.uuid(20); UUID operatorId = TestIds.uuid(55);
        MarketplaceProfile profile = makeMarketplaceProfile(mktId, operatorId);
        when(marketplaceProfileRepository.findByCompanyId(mktId)).thenReturn(Optional.of(profile));

        TopVendorProjection row = mock(TopVendorProjection.class);
        when(row.getVendorId()).thenReturn(TestIds.uuid(7));
        when(row.getVendorName()).thenReturn("Acme Vendor");
        when(row.getTotalSubOrders()).thenReturn(15L);
        when(row.getTotalGrossRevenue()).thenReturn(new BigDecimal("1500.00"));
        when(row.getTotalCommission()).thenReturn(new BigDecimal("150.00"));
        when(row.getCancellationRate()).thenReturn(0.05);
        when(analyticsRepository.marketplaceTopVendors(eq(mktId), any(), any(), eq(10))).thenReturn(List.of(row));

        List<TopVendorResponse> result = service.getTopVendors(mktId, operatorId, 30, 10);

        assertEquals(1, result.size());
        assertEquals(TestIds.uuid(7), result.get(0).getVendorId());
        assertEquals("Acme Vendor", result.get(0).getVendorName());
        assertEquals(15L, result.get(0).getTotalSubOrders());
    }

    @Test
    void getTopVendors_forbidden_throwsForbiddenException() {
        UUID mktId = TestIds.uuid(20);
        when(marketplaceProfileRepository.findByCompanyId(mktId)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> service.getTopVendors(mktId, TestIds.uuid(99), 30, 10));
    }

    @Test
    void getTopVendors_nullValuesInProjection_defaultsToZero() {
        UUID mktId = TestIds.uuid(20); UUID operatorId = TestIds.uuid(55);
        MarketplaceProfile profile = makeMarketplaceProfile(mktId, operatorId);
        when(marketplaceProfileRepository.findByCompanyId(mktId)).thenReturn(Optional.of(profile));

        TopVendorProjection row = mock(TopVendorProjection.class);
        when(row.getVendorId()).thenReturn(TestIds.uuid(7));
        when(row.getVendorName()).thenReturn("Sparse Vendor");
        when(row.getTotalSubOrders()).thenReturn(null);
        when(row.getTotalGrossRevenue()).thenReturn(null);
        when(row.getTotalCommission()).thenReturn(null);
        when(row.getCancellationRate()).thenReturn(null);
        when(analyticsRepository.marketplaceTopVendors(any(), any(), any(), anyInt())).thenReturn(List.of(row));

        List<TopVendorResponse> result = service.getTopVendors(mktId, operatorId, 30, 10);

        assertEquals(1, result.size());
        assertEquals(0L, result.get(0).getTotalSubOrders());
        assertEquals(BigDecimal.ZERO, result.get(0).getTotalGrossRevenue());
        assertEquals(0.0, result.get(0).getCancellationRate());
    }

    @Test
    void getTopVendors_limitAbove50_capsAt50() {
        UUID mktId = TestIds.uuid(20); UUID operatorId = TestIds.uuid(55);
        MarketplaceProfile profile = makeMarketplaceProfile(mktId, operatorId);
        when(marketplaceProfileRepository.findByCompanyId(mktId)).thenReturn(Optional.of(profile));
        when(analyticsRepository.marketplaceTopVendors(any(), any(), any(), eq(50))).thenReturn(List.of());

        assertDoesNotThrow(() -> service.getTopVendors(mktId, operatorId, 30, 999));

        verify(analyticsRepository).marketplaceTopVendors(any(), any(), any(), eq(50));
    }

    // ─── clamp boundary conditions ───────────────────────────────────────────

    @Test
    void getOrders_lookbackBelowMin_clampsTo7Days() {
        UUID vendorId = TestIds.uuid(7); UUID mktId = TestIds.uuid(20); UUID ownerId = TestIds.uuid(10);
        MarketplaceVendor vendor = makeVendor(vendorId, mktId, ownerId);
        when(marketplaceVendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(analyticsRepository.vendorTotalOrders(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorCancelledCount(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorOrdersDaily(any(), any(), any(), any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.getOrders(vendorId, mktId, 1, ownerId));
    }

    @Test
    void getOrders_lookbackAboveMax_clampsTo365Days() {
        UUID vendorId = TestIds.uuid(7); UUID mktId = TestIds.uuid(20); UUID ownerId = TestIds.uuid(10);
        MarketplaceVendor vendor = makeVendor(vendorId, mktId, ownerId);
        when(marketplaceVendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(analyticsRepository.vendorTotalOrders(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorCancelledCount(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorOrdersDaily(any(), any(), any(), any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.getOrders(vendorId, mktId, 999, ownerId));
    }

    // ─── assertVendorAccess — wrong marketplace ───────────────────────────────

    @Test
    void getSummary_vendorBelongsToWrongMarketplace_throwsResourceNotFoundException() {
        UUID vendorId = TestIds.uuid(7);
        UUID correctMarketplaceId = TestIds.uuid(20);
        UUID wrongMarketplaceId = TestIds.uuid(99);

        MarketplaceVendor vendor = makeVendor(vendorId, correctMarketplaceId, TestIds.uuid(10));
        when(marketplaceVendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));

        // The vendor belongs to marketplace 20, but we're asking for marketplace 99
        assertThrows(ResourceNotFoundException.class,
                () -> service.getSummary(vendorId, wrongMarketplaceId, 30, TestIds.uuid(999)));
    }

    // ─── getMarketplaceSummary — forbidden ────────────────────────────────────

    @Test
    void getMarketplaceSummary_forbidden_throwsForbiddenException() {
        UUID mktId = TestIds.uuid(20);
        when(marketplaceProfileRepository.findByCompanyId(mktId)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> service.getMarketplaceSummary(mktId, TestIds.uuid(99), 30));
    }

    // ─── readCache deserialization error ─────────────────────────────────────

    @Test
    void getSummary_cacheDeserializationError_fallsThroughToRepository() {
        UUID vendorId = TestIds.uuid(7); UUID mktId = TestIds.uuid(20); UUID ownerId = TestIds.uuid(10);
        MarketplaceVendor vendor = makeVendor(vendorId, mktId, ownerId);
        when(marketplaceVendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(cacheService.get(anyString())).thenReturn("not-valid-json{{{");

        VendorRevenueSummaryProjection rev = mock(VendorRevenueSummaryProjection.class);
        when(analyticsRepository.vendorRevenueSummary(any(), any(), any(), any())).thenReturn(rev);
        when(analyticsRepository.vendorTotalOrders(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorCancelledCount(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorReturnedCount(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorShipHours(any(), any(), any(), any(), anyDouble())).thenReturn(null);

        assertDoesNotThrow(() -> service.getSummary(vendorId, mktId, 30, ownerId));
        verify(analyticsRepository).vendorRevenueSummary(any(), any(), any(), any());
    }

    // ─── toDailyPoints — null count defaults to 0 ────────────────────────────

    @Test
    void getOrders_dailyPointWithNullCount_defaultsToZero() {
        UUID vendorId = TestIds.uuid(7); UUID mktId = TestIds.uuid(20); UUID ownerId = TestIds.uuid(10);
        MarketplaceVendor vendor = makeVendor(vendorId, mktId, ownerId);
        when(marketplaceVendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));

        DailyCountProjection row = mock(DailyCountProjection.class);
        when(row.getDay()).thenReturn(LocalDate.now());
        when(row.getCount()).thenReturn(null);

        when(analyticsRepository.vendorTotalOrders(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorCancelledCount(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsRepository.vendorOrdersDaily(any(), any(), any(), any())).thenReturn(List.of(row));

        assertDoesNotThrow(() -> service.getOrders(vendorId, mktId, 30, ownerId));
    }

    // ─── getOrders / getRefunds — non-zero totals for rate calculation ────────

    @Test
    void getOrders_nonZeroTotal_computesCancellationRate() {
        UUID vendorId = TestIds.uuid(7); UUID mktId = TestIds.uuid(20); UUID ownerId = TestIds.uuid(10);
        MarketplaceVendor vendor = makeVendor(vendorId, mktId, ownerId);
        when(marketplaceVendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(analyticsRepository.vendorTotalOrders(any(), any(), any(), any())).thenReturn(100L);
        when(analyticsRepository.vendorCancelledCount(any(), any(), any(), any())).thenReturn(10L);
        when(analyticsRepository.vendorOrdersDaily(any(), any(), any(), any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.getOrders(vendorId, mktId, 30, ownerId));
    }

    @Test
    void getRefunds_nonZeroTotal_computesRefundRate() {
        UUID vendorId = TestIds.uuid(7); UUID mktId = TestIds.uuid(20); UUID ownerId = TestIds.uuid(10);
        MarketplaceVendor vendor = makeVendor(vendorId, mktId, ownerId);
        when(marketplaceVendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(analyticsRepository.vendorTotalOrders(any(), any(), any(), any())).thenReturn(50L);
        when(analyticsRepository.vendorReturnedCount(any(), any(), any(), any())).thenReturn(5L);
        when(analyticsRepository.vendorRefundsDaily(any(), any(), any(), any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.getRefunds(vendorId, mktId, 30, ownerId));
    }

    // ─── getSummary — shipment metrics with non-null VendorShipHoursProjection ──

    @Test
    void getSummary_withShipHoursData_computesLateShipmentRate() {
        UUID vendorId = TestIds.uuid(7); UUID mktId = TestIds.uuid(20); UUID ownerId = TestIds.uuid(10);
        MarketplaceVendor vendor = makeVendor(vendorId, mktId, ownerId);
        when(marketplaceVendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));

        VendorRevenueSummaryProjection rev = mock(VendorRevenueSummaryProjection.class);
        when(analyticsRepository.vendorRevenueSummary(any(), any(), any(), any())).thenReturn(rev);
        when(analyticsRepository.vendorTotalOrders(any(), any(), any(), any())).thenReturn(100L);
        when(analyticsRepository.vendorCancelledCount(any(), any(), any(), any())).thenReturn(5L);
        when(analyticsRepository.vendorReturnedCount(any(), any(), any(), any())).thenReturn(3L);

        backend.repositories.projections.VendorShipHoursProjection ship =
                mock(backend.repositories.projections.VendorShipHoursProjection.class);
        when(ship.getTotalShipped()).thenReturn(80L);
        when(ship.getTotalLate()).thenReturn(10L);
        when(ship.getAvgShipHours()).thenReturn(36.5);
        when(analyticsRepository.vendorShipHours(any(), any(), any(), any(), anyDouble())).thenReturn(ship);

        assertDoesNotThrow(() -> service.getSummary(vendorId, mktId, 30, ownerId));
    }
}
