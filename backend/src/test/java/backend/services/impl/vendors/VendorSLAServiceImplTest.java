package backend.services.impl.vendors;

import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.sla.VendorSLAMetricResponse;
import backend.exceptions.http.ForbiddenException;
import backend.models.core.Company;
import backend.models.core.MarketplaceVendor;
import backend.models.core.User;
import backend.models.core.VendorSLAMetric;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.VendorSLABreachRepository;
import backend.repositories.VendorSLAMetricRepository;
import backend.repositories.VendorSLAPolicyRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
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

class VendorSLAServiceImplTest {

    private VendorSLAPolicyRepository policyRepository;
    private VendorSLAMetricRepository metricRepository;
    private VendorSLABreachRepository breachRepository;
    private MarketplaceProfileRepository marketplaceProfileRepository;
    private MarketplaceVendorRepository marketplaceVendorRepository;
    private VendorSLAServiceImpl service;

    @BeforeEach
    void setUp() {
        policyRepository             = mock(VendorSLAPolicyRepository.class);
        metricRepository             = mock(VendorSLAMetricRepository.class);
        breachRepository             = mock(VendorSLABreachRepository.class);
        marketplaceProfileRepository = mock(MarketplaceProfileRepository.class);
        marketplaceVendorRepository  = mock(MarketplaceVendorRepository.class);

        service = new VendorSLAServiceImpl(
                policyRepository,
                metricRepository,
                breachRepository,
                marketplaceProfileRepository,
                marketplaceVendorRepository);
    }

    @Test
    void listMetrics_throwsWhenActorCannotAccessVendor() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(20), TestIds.uuid(10));
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(marketplaceProfileRepository.findByCompanyId(TestIds.uuid(20))).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> service.listMetrics(TestIds.uuid(20), TestIds.uuid(7), TestIds.uuid(99), 0, 10));
    }

    @Test
    void listMetrics_allowsVendorOwner() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(20), TestIds.uuid(10));
        VendorSLAMetric metric = new VendorSLAMetric();
        metric.setId(TestIds.uuid(1));
        metric.setVendorId(TestIds.uuid(7));
        metric.setMarketplaceId(TestIds.uuid(20));
        metric.setDate(LocalDate.now());
        metric.setTotalOrders(12);

        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(metricRepository.findByVendorId(eq(TestIds.uuid(7)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(metric)));

        PagedResponse<VendorSLAMetricResponse> response =
                service.listMetrics(TestIds.uuid(20), TestIds.uuid(7), TestIds.uuid(10), 0, 10);

        assertEquals(1, response.getItems().size());
        assertEquals(TestIds.uuid(7), response.getItems().get(0).getVendorId());
        verify(metricRepository).findByVendorId(eq(TestIds.uuid(7)), any(Pageable.class));
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
}
