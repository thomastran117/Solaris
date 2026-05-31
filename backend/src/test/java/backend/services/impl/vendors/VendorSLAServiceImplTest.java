package backend.services.impl.vendors;

import backend.dtos.requests.sla.CreateSLAPolicyRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.sla.VendorSLABreachResponse;
import backend.dtos.responses.sla.VendorSLAMetricResponse;
import backend.dtos.responses.sla.VendorSLAPolicyResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.MarketplaceProfile;
import backend.models.core.MarketplaceVendor;
import backend.models.core.User;
import backend.models.core.VendorSLABreach;
import backend.models.core.VendorSLAMetric;
import backend.models.core.VendorSLAPolicy;
import backend.models.enums.SLABreachAction;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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

    // ─── createPolicy ─────────────────────────────────────────────────────────

    @Test
    void createPolicy_happyPath_savesPolicy() {
        // assertOperator uses == for UUID comparison — reuse the same object reference
        UUID operatorId = TestIds.uuid(55);
        User operatorUser = new User();
        operatorUser.setId(operatorId);
        Company mpCompany = new Company();
        mpCompany.setId(TestIds.uuid(20));
        mpCompany.setOwner(operatorUser);
        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setCompany(mpCompany);
        when(marketplaceProfileRepository.findByCompanyId(TestIds.uuid(20))).thenReturn(Optional.of(profile));

        VendorSLAPolicy saved = new VendorSLAPolicy();
        saved.setId(TestIds.uuid(1));
        saved.setMarketplaceId(TestIds.uuid(20));
        saved.setName("Standard SLA");
        saved.setBreachAction(SLABreachAction.WARN);
        saved.setActive(true);
        when(policyRepository.save(any())).thenReturn(saved);

        CreateSLAPolicyRequest req = mock(CreateSLAPolicyRequest.class);
        when(req.getName()).thenReturn("Standard SLA");
        when(req.getBreachAction()).thenReturn("WARN");
        when(req.getTargetShipHours()).thenReturn(48.0);
        when(req.getTargetResponseHours()).thenReturn(24.0);
        when(req.getMaxCancellationRate()).thenReturn(0.05);
        when(req.getMaxRefundRate()).thenReturn(0.03);
        when(req.getMaxLateShipmentRate()).thenReturn(0.10);
        when(req.getEvaluationWindowDays()).thenReturn(30);

        VendorSLAPolicyResponse resp = service.createPolicy(TestIds.uuid(20), operatorId, req);

        assertEquals("Standard SLA", resp.getName());
        verify(policyRepository).save(any(VendorSLAPolicy.class));
    }

    @Test
    void createPolicy_invalidBreachAction_throwsBadRequest() {
        UUID operatorId = TestIds.uuid(55);
        User operatorUser = new User();
        operatorUser.setId(operatorId);
        Company mpCompany = new Company();
        mpCompany.setId(TestIds.uuid(20));
        mpCompany.setOwner(operatorUser);
        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setCompany(mpCompany);
        when(marketplaceProfileRepository.findByCompanyId(TestIds.uuid(20))).thenReturn(Optional.of(profile));

        CreateSLAPolicyRequest req = mock(CreateSLAPolicyRequest.class);
        when(req.getBreachAction()).thenReturn("INVALID_ACTION");

        assertThrows(BadRequestException.class,
                () -> service.createPolicy(TestIds.uuid(20), operatorId, req));
    }

    // ─── getActivePolicy ──────────────────────────────────────────────────────

    @Test
    void getActivePolicy_noActivePolicy_throwsResourceNotFound() {
        when(policyRepository.findFirstByMarketplaceIdAndActiveTrue(TestIds.uuid(20)))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getActivePolicy(TestIds.uuid(20)));
    }

    @Test
    void getActivePolicy_happyPath_returnsPolicy() {
        VendorSLAPolicy policy = new VendorSLAPolicy();
        policy.setId(TestIds.uuid(1));
        policy.setMarketplaceId(TestIds.uuid(20));
        policy.setName("Active SLA");
        policy.setBreachAction(SLABreachAction.WARN);
        policy.setActive(true);
        when(policyRepository.findFirstByMarketplaceIdAndActiveTrue(TestIds.uuid(20)))
                .thenReturn(Optional.of(policy));

        VendorSLAPolicyResponse resp = service.getActivePolicy(TestIds.uuid(20));

        assertEquals("Active SLA", resp.getName());
    }

    // ─── listPolicies ─────────────────────────────────────────────────────────

    @Test
    void listPolicies_returnsMappedList() {
        VendorSLAPolicy policy = new VendorSLAPolicy();
        policy.setId(TestIds.uuid(1));
        policy.setMarketplaceId(TestIds.uuid(20));
        policy.setName("Policy A");
        policy.setBreachAction(SLABreachAction.WARN);
        when(policyRepository.findByMarketplaceId(TestIds.uuid(20))).thenReturn(List.of(policy));

        List<VendorSLAPolicyResponse> result = service.listPolicies(TestIds.uuid(20));

        assertEquals(1, result.size());
        assertEquals("Policy A", result.get(0).getName());
    }

    // ─── getLatestMetric ──────────────────────────────────────────────────────

    @Test
    void getLatestMetric_noMetrics_throwsResourceNotFound() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(20), TestIds.uuid(10));
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(metricRepository.findByVendorIdOrderByDateDesc(TestIds.uuid(7))).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getLatestMetric(TestIds.uuid(20), TestIds.uuid(7), TestIds.uuid(10)));
    }

    @Test
    void getLatestMetric_happyPath_returnsFirst() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(20), TestIds.uuid(10));
        VendorSLAMetric metric = new VendorSLAMetric();
        metric.setId(TestIds.uuid(1));
        metric.setVendorId(TestIds.uuid(7));
        metric.setMarketplaceId(TestIds.uuid(20));
        metric.setDate(LocalDate.now());
        metric.setTotalOrders(5);
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(metricRepository.findByVendorIdOrderByDateDesc(TestIds.uuid(7))).thenReturn(List.of(metric));

        VendorSLAMetricResponse resp = service.getLatestMetric(TestIds.uuid(20), TestIds.uuid(7), TestIds.uuid(10));

        assertEquals(TestIds.uuid(7), resp.getVendorId());
    }

    // ─── listBreaches ─────────────────────────────────────────────────────────

    @Test
    void listBreaches_happyPath_returnsMappedPage() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(20), TestIds.uuid(10));
        VendorSLABreach breach = new VendorSLABreach();
        breach.setId(TestIds.uuid(1));
        breach.setVendorId(TestIds.uuid(7));
        breach.setPolicyId(TestIds.uuid(5));
        breach.setMetric("cancellationRate");
        breach.setActualValue(0.15);
        breach.setThreshold(0.05);
        breach.setDetectedAt(Instant.now());
        breach.setActionTaken(SLABreachAction.WARN);
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(breachRepository.findByVendorId(eq(TestIds.uuid(7)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(breach)));

        PagedResponse<VendorSLABreachResponse> resp =
                service.listBreaches(TestIds.uuid(20), TestIds.uuid(7), TestIds.uuid(10), 0, 10);

        assertEquals(1, resp.getItems().size());
    }

    // ─── resolveBreach ────────────────────────────────────────────────────────

    @Test
    void resolveBreach_happyPath_setsResolvedAt() {
        UUID operatorId = TestIds.uuid(55);
        User operatorUser = new User();
        operatorUser.setId(operatorId);
        Company mpCompany = new Company();
        mpCompany.setId(TestIds.uuid(20));
        mpCompany.setOwner(operatorUser);
        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setCompany(mpCompany);
        when(marketplaceProfileRepository.findByCompanyId(TestIds.uuid(20))).thenReturn(Optional.of(profile));

        VendorSLABreach breach = new VendorSLABreach();
        breach.setId(TestIds.uuid(1));
        breach.setVendorId(TestIds.uuid(7));
        breach.setPolicyId(TestIds.uuid(5));
        breach.setMetric("refundRate");
        breach.setActualValue(0.08);
        breach.setThreshold(0.03);
        breach.setDetectedAt(Instant.now().minusSeconds(3600));
        breach.setActionTaken(SLABreachAction.WARN);
        // resolvedAt is null — can be resolved
        when(breachRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(breach));
        when(breachRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VendorSLABreachResponse resp = service.resolveBreach(TestIds.uuid(1), operatorId, TestIds.uuid(20));

        assertNotNull(resp.getResolvedAt());
    }

    @Test
    void resolveBreach_alreadyResolved_throwsBadRequest() {
        UUID operatorId = TestIds.uuid(55);
        User operatorUser = new User();
        operatorUser.setId(operatorId);
        Company mpCompany = new Company();
        mpCompany.setId(TestIds.uuid(20));
        mpCompany.setOwner(operatorUser);
        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setCompany(mpCompany);
        when(marketplaceProfileRepository.findByCompanyId(TestIds.uuid(20))).thenReturn(Optional.of(profile));

        VendorSLABreach breach = new VendorSLABreach();
        breach.setResolvedAt(Instant.now()); // already resolved
        breach.setActionTaken(SLABreachAction.WARN);
        when(breachRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(breach));

        assertThrows(BadRequestException.class,
                () -> service.resolveBreach(TestIds.uuid(1), operatorId, TestIds.uuid(20)));
    }

    @Test
    void resolveBreach_breachNotFound_throwsResourceNotFound() {
        UUID operatorId = TestIds.uuid(55);
        User operatorUser = new User();
        operatorUser.setId(operatorId);
        Company mpCompany = new Company();
        mpCompany.setId(TestIds.uuid(20));
        mpCompany.setOwner(operatorUser);
        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setCompany(mpCompany);
        when(marketplaceProfileRepository.findByCompanyId(TestIds.uuid(20))).thenReturn(Optional.of(profile));
        when(breachRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.resolveBreach(TestIds.uuid(99), operatorId, TestIds.uuid(20)));
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
