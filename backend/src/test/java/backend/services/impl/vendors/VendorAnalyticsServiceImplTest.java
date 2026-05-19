package backend.services.impl.vendors;

import backend.dtos.responses.vendor.VendorPayoutsMetricResponse;
import backend.exceptions.http.ForbiddenException;
import backend.models.core.Company;
import backend.models.core.MarketplaceProfile;
import backend.models.core.MarketplaceVendor;
import backend.models.core.User;
import backend.models.enums.PayoutStatus;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.VendorAnalyticsRepository;
import backend.repositories.VendorPayoutRepository;
import backend.services.intf.CacheService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
