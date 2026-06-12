package backend.services.impl.promotions;

import backend.dtos.requests.pricing.CreatePromotionRuleRequest;
import backend.dtos.requests.pricing.UpdatePromotionRuleRequest;
import backend.dtos.responses.pricing.PromotionRuleResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CustomerSegment;
import backend.models.core.Product;
import backend.models.core.ProductBundle;
import backend.models.core.PromotionRule;
import backend.models.enums.CompanyCapability;
import backend.models.enums.DiscountStatus;
import backend.models.enums.ProductStatus;
import backend.models.enums.PromotionRuleType;
import backend.repositories.BundleRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.CustomerSegmentRepository;
import backend.repositories.ProductRepository;
import backend.repositories.PromotionRuleRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.pricing.config.PromotionConfigValidator;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromotionRuleServiceImplTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID OWNER_ID   = TestIds.uuid(2);
    private static final UUID RULE_ID    = TestIds.uuid(3);
    private static final UUID PRODUCT_ID = TestIds.uuid(4);
    private static final UUID BUNDLE_ID  = TestIds.uuid(5);
    private static final UUID SEGMENT_ID = TestIds.uuid(6);

    private PromotionRuleRepository ruleRepository;
    private CompanyRepository companyRepository;
    private CompanyAccessService companyAccessService;
    private ProductRepository productRepository;
    private BundleRepository bundleRepository;
    private CustomerSegmentRepository segmentRepository;
    private PromotionConfigValidator configValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PromotionRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        ruleRepository       = mock(PromotionRuleRepository.class);
        companyRepository    = mock(CompanyRepository.class);
        companyAccessService = mock(CompanyAccessService.class);
        productRepository    = mock(ProductRepository.class);
        bundleRepository     = mock(BundleRepository.class);
        segmentRepository    = mock(CustomerSegmentRepository.class);
        configValidator      = mock(PromotionConfigValidator.class);

        service = new PromotionRuleServiceImpl(
                ruleRepository, companyRepository, companyAccessService,
                productRepository, bundleRepository, segmentRepository,
                configValidator, objectMapper);

        when(companyAccessService.require(eq(COMPANY_ID), eq(OWNER_ID), any(CompanyCapability.class)))
                .thenReturn(makeCompany());
        when(companyRepository.getReferenceById(COMPANY_ID)).thenReturn(makeCompany());
        when(configValidator.validateAndSerialise(any(PromotionRuleType.class), any()))
                .thenReturn("{}");
        when(ruleRepository.save(any(PromotionRule.class))).thenAnswer(inv -> {
            PromotionRule r = inv.getArgument(0);
            if (r.getId() == null) r.setId(RULE_ID);
            return r;
        });
    }

    // ─── createRule ───────────────────────────────────────────────────────────

    @Test
    void createRule_happyPath_savesAndReturnsResponse() {
        PromotionRuleResponse result = service.createRule(COMPANY_ID, OWNER_ID, minimalCreateRequest());

        verify(ruleRepository).save(any(PromotionRule.class));
        assertNotNull(result);
        assertEquals("Test Rule", result.name());
    }

    @Test
    void createRule_invalidRuleType_throwsBadRequest() {
        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setRuleType("BOGUS");

        assertThrows(BadRequestException.class,
                () -> service.createRule(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createRule_endDateBeforeStartDate_throwsBadRequest() {
        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setStartDate(Instant.now().plusSeconds(3600));
        req.setEndDate(Instant.now().plusSeconds(60));

        assertThrows(BadRequestException.class,
                () -> service.createRule(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createRule_endDateEqualStartDate_throwsBadRequest() {
        Instant same = Instant.now().plusSeconds(3600);
        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setStartDate(same);
        req.setEndDate(same);

        assertThrows(BadRequestException.class,
                () -> service.createRule(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createRule_nullPriority_defaultsTo100() {
        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setPriority(null);

        service.createRule(COMPANY_ID, OWNER_ID, req);

        ArgumentCaptor<PromotionRule> captor = ArgumentCaptor.forClass(PromotionRule.class);
        verify(ruleRepository).save(captor.capture());
        assertEquals(100, captor.getValue().getPriority());
    }

    @Test
    void createRule_nullStackable_defaultsFalse() {
        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setStackable(null);

        service.createRule(COMPANY_ID, OWNER_ID, req);

        ArgumentCaptor<PromotionRule> captor = ArgumentCaptor.forClass(PromotionRule.class);
        verify(ruleRepository).save(captor.capture());
        assertFalse(captor.getValue().isStackable());
    }

    @Test
    void createRule_targetProductIdNotInCompany_throwsBadRequest() {
        when(productRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID))).thenReturn(List.of());

        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setTargetProductIds(List.of(PRODUCT_ID));

        assertThrows(BadRequestException.class,
                () -> service.createRule(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createRule_targetBundleIdNotInCompany_throwsBadRequest() {
        when(bundleRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID))).thenReturn(List.of());

        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setTargetBundleIds(List.of(BUNDLE_ID));

        assertThrows(BadRequestException.class,
                () -> service.createRule(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createRule_targetSegmentIdNotFound_throwsBadRequest() {
        when(segmentRepository.findAllById(anyList())).thenReturn(List.of());

        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setTargetSegmentIds(List.of(SEGMENT_ID));

        assertThrows(BadRequestException.class,
                () -> service.createRule(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createRule_emptyTargetProductIds_producesEmptySet() {
        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setTargetProductIds(List.of());

        service.createRule(COMPANY_ID, OWNER_ID, req);

        verify(productRepository, never()).findAllByIdInAndCompanyId(any(), any());
    }

    @Test
    void createRule_nullTargetProductIds_producesEmptySet() {
        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setTargetProductIds(null);

        service.createRule(COMPANY_ID, OWNER_ID, req);

        verify(productRepository, never()).findAllByIdInAndCompanyId(any(), any());
    }

    @Test
    void createRule_fundedByCompanyIdNotFound_throwsBadRequest() {
        UUID funderId = TestIds.uuid(99);
        when(companyRepository.findById(funderId)).thenReturn(Optional.empty());

        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setFundedByCompanyId(funderId);

        assertThrows(BadRequestException.class,
                () -> service.createRule(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createRule_fundedByCompanyIdNull_noFunderSet() {
        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setFundedByCompanyId(null);

        service.createRule(COMPANY_ID, OWNER_ID, req);

        ArgumentCaptor<PromotionRule> captor = ArgumentCaptor.forClass(PromotionRule.class);
        verify(ruleRepository).save(captor.capture());
        assertNull(captor.getValue().getFundedByCompany());
    }

    @Test
    void createRule_configValidatorCalled() {
        service.createRule(COMPANY_ID, OWNER_ID, minimalCreateRequest());

        verify(configValidator).validateAndSerialise(eq(PromotionRuleType.PERCENTAGE_OFF), any());
    }

    @Test
    void createRule_pastEndDate_statusComputedAsExpired() {
        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setEndDate(Instant.now().minusSeconds(3600));

        PromotionRuleResponse result = service.createRule(COMPANY_ID, OWNER_ID, req);

        assertEquals(DiscountStatus.EXPIRED, result.status());
    }

    @Test
    void createRule_futureEndDate_usesStoredStatus() {
        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setEndDate(Instant.now().plusSeconds(3600));

        PromotionRuleResponse result = service.createRule(COMPANY_ID, OWNER_ID, req);

        assertEquals(DiscountStatus.ACTIVE, result.status());
    }

    @Test
    void createRule_validProductTargets_resolvesCorrectly() {
        Product product = makeProduct(PRODUCT_ID);
        when(productRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID))).thenReturn(List.of(product));

        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setTargetProductIds(List.of(PRODUCT_ID));

        PromotionRuleResponse result = service.createRule(COMPANY_ID, OWNER_ID, req);

        assertEquals(List.of(PRODUCT_ID), result.targetProductIds());
    }

    // ─── updateRule ───────────────────────────────────────────────────────────

    @Test
    void updateRule_nameAndDescription_updatesFields() {
        PromotionRule rule = makeRule(RULE_ID);
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(rule));

        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setName("New Name");
        req.setDescription("New Desc");

        PromotionRuleResponse result = service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req);

        assertEquals("New Name", result.name());
        assertEquals("New Desc", result.description());
    }

    @Test
    void updateRule_setExpiredStatus_throwsBadRequest() {
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(makeRule(RULE_ID)));

        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setStatus("EXPIRED");

        assertThrows(BadRequestException.class,
                () -> service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req));
    }

    @Test
    void updateRule_setActiveStatus_setsActive() {
        PromotionRule rule = makeRule(RULE_ID);
        rule.setStatus(DiscountStatus.DISABLED);
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(rule));

        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setStatus("ACTIVE");

        service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req);

        assertEquals(DiscountStatus.ACTIVE, rule.getStatus());
    }

    @Test
    void updateRule_setDisabledStatus_setsDisabled() {
        PromotionRule rule = makeRule(RULE_ID);
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(rule));

        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setStatus("DISABLED");

        service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req);

        assertEquals(DiscountStatus.DISABLED, rule.getStatus());
    }

    @Test
    void updateRule_invalidStatus_throwsBadRequest() {
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(makeRule(RULE_ID)));

        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setStatus("UNKNOWN");

        assertThrows(BadRequestException.class,
                () -> service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req));
    }

    @Test
    void updateRule_endDateCrossCheckedWithExistingStart_throwsBadRequest() {
        PromotionRule rule = makeRule(RULE_ID);
        rule.setStartDate(Instant.now().plusSeconds(3600));
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(rule));

        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setEndDate(Instant.now().plusSeconds(60)); // before existing start

        assertThrows(BadRequestException.class,
                () -> service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req));
    }

    @Test
    void updateRule_configUpdated_validatedAgainstCurrentRuleType() {
        PromotionRule rule = makeRule(RULE_ID);
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(rule));

        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setConfig(objectMapper.createObjectNode());

        service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req);

        verify(configValidator).validateAndSerialise(eq(PromotionRuleType.PERCENTAGE_OFF), any());
    }

    @Test
    void updateRule_targetProductsReplaced_whenProvided() {
        Product product = makeProduct(PRODUCT_ID);
        when(productRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID))).thenReturn(List.of(product));

        PromotionRule rule = makeRule(RULE_ID);
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(rule));

        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setTargetProductIds(List.of(PRODUCT_ID));

        PromotionRuleResponse result = service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req);

        assertEquals(List.of(PRODUCT_ID), result.targetProductIds());
    }

    @Test
    void updateRule_targetProductsUnchanged_whenNull() {
        PromotionRule rule = makeRule(RULE_ID);
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(rule));

        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setTargetProductIds(null); // null = no change

        service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req);

        verify(productRepository, never()).findAllByIdInAndCompanyId(any(), any());
    }

    @Test
    void updateRule_notFound_throwsResourceNotFound() {
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, new UpdatePromotionRuleRequest()));
    }

    // ─── deleteRule ───────────────────────────────────────────────────────────

    @Test
    void deleteRule_happyPath_clearsTargetsAndDeletes() {
        PromotionRule rule = makeRule(RULE_ID);
        // Add something to the sets so we can verify they get cleared
        Product product = makeProduct(PRODUCT_ID);
        rule.getTargetProducts().add(product);

        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(rule));

        service.deleteRule(COMPANY_ID, RULE_ID, OWNER_ID);

        assertTrue(rule.getTargetProducts().isEmpty());
        assertTrue(rule.getTargetBundles().isEmpty());
        assertTrue(rule.getTargetSegments().isEmpty());
        verify(ruleRepository).delete(rule);
    }

    @Test
    void deleteRule_notFound_throwsResourceNotFound() {
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteRule(COMPANY_ID, RULE_ID, OWNER_ID));
    }

    // ─── getRule / listRules ──────────────────────────────────────────────────

    @Test
    void getRule_happyPath_returnsResponse() {
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(makeRule(RULE_ID)));

        PromotionRuleResponse result = service.getRule(COMPANY_ID, RULE_ID, OWNER_ID);

        assertNotNull(result);
        assertEquals("Test Rule", result.name());
    }

    @Test
    void getRule_notFound_throwsResourceNotFound() {
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getRule(COMPANY_ID, RULE_ID, OWNER_ID));
    }

    @Test
    void listRules_delegatesToRepository() {
        when(ruleRepository.findAllByCompanyId(eq(COMPANY_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listRules(COMPANY_ID, OWNER_ID, 0, 20);

        verify(ruleRepository).findAllByCompanyId(eq(COMPANY_ID), any(Pageable.class));
    }

    // ─── createRule — valid bundle/segment targets ────────────────────────────

    @Test
    void createRule_validBundleTargets_resolvesCorrectly() {
        ProductBundle bundle = new ProductBundle();
        bundle.setId(BUNDLE_ID);
        when(bundleRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID))).thenReturn(List.of(bundle));

        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setTargetBundleIds(List.of(BUNDLE_ID));

        PromotionRuleResponse result = service.createRule(COMPANY_ID, OWNER_ID, req);

        assertEquals(List.of(BUNDLE_ID), result.targetBundleIds());
    }

    @Test
    void createRule_validSegmentTargets_resolvesCorrectly() {
        CustomerSegment seg = new CustomerSegment();
        seg.setId(SEGMENT_ID);
        when(segmentRepository.findAllById(anyList())).thenReturn(List.of(seg));

        CreatePromotionRuleRequest req = minimalCreateRequest();
        req.setTargetSegmentIds(List.of(SEGMENT_ID));

        PromotionRuleResponse result = service.createRule(COMPANY_ID, OWNER_ID, req);

        assertEquals(List.of(SEGMENT_ID), result.targetSegmentIds());
    }

    // ─── updateRule — additional branches ────────────────────────────────────

    @Test
    void updateRule_priorityAndStackable_updatesFields() {
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(makeRule(RULE_ID)));

        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setPriority(50);
        req.setStackable(true);

        PromotionRuleResponse result = service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req);

        assertEquals(50, result.priority());
        assertTrue(result.stackable());
    }

    @Test
    void updateRule_startDate_provided_updates() {
        Instant newStart = Instant.now().plusSeconds(3600);
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(makeRule(RULE_ID)));

        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setStartDate(newStart);

        PromotionRuleResponse result = service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req);

        assertEquals(newStart, result.startDate());
    }

    @Test
    void updateRule_endDate_validAfterExistingStart_updates() {
        PromotionRule rule = makeRule(RULE_ID);
        rule.setStartDate(Instant.now().plusSeconds(60));
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(rule));

        Instant newEnd = Instant.now().plusSeconds(7200);
        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setEndDate(newEnd);

        PromotionRuleResponse result = service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req);

        assertEquals(newEnd, result.endDate());
    }

    @Test
    void updateRule_minCartAmount_maxUses_maxUsesPerUser_updated() {
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(makeRule(RULE_ID)));

        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setMinCartAmount(new BigDecimal("25.00"));
        req.setMaxUses(100);
        req.setMaxUsesPerUser(2);

        PromotionRuleResponse result = service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req);

        assertEquals(new BigDecimal("25.00"), result.minCartAmount());
        assertEquals(100, result.maxUses());
        assertEquals(2, result.maxUsesPerUser());
    }

    @Test
    void updateRule_fundedByCompanyId_provided_setsCompany() {
        UUID funderId = TestIds.uuid(99);
        Company funder = new Company();
        funder.setId(funderId);
        when(companyRepository.findById(funderId)).thenReturn(Optional.of(funder));
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(makeRule(RULE_ID)));

        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setFundedByCompanyId(funderId);

        PromotionRuleResponse result = service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req);

        assertEquals(funderId, result.fundedByCompanyId());
    }

    @Test
    void updateRule_targetBundlesReplaced_whenProvided() {
        ProductBundle bundle = new ProductBundle();
        bundle.setId(BUNDLE_ID);
        when(bundleRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID))).thenReturn(List.of(bundle));
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(makeRule(RULE_ID)));

        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setTargetBundleIds(List.of(BUNDLE_ID));

        PromotionRuleResponse result = service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req);

        assertEquals(List.of(BUNDLE_ID), result.targetBundleIds());
    }

    @Test
    void updateRule_targetSegmentsReplaced_whenProvided() {
        CustomerSegment seg = new CustomerSegment();
        seg.setId(SEGMENT_ID);
        when(segmentRepository.findAllById(anyList())).thenReturn(List.of(seg));
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(makeRule(RULE_ID)));

        UpdatePromotionRuleRequest req = new UpdatePromotionRuleRequest();
        req.setTargetSegmentIds(List.of(SEGMENT_ID));

        PromotionRuleResponse result = service.updateRule(COMPANY_ID, RULE_ID, OWNER_ID, req);

        assertEquals(List.of(SEGMENT_ID), result.targetSegmentIds());
    }

    // ─── getRule — additional branches ───────────────────────────────────────

    @Test
    void getRule_withFundedByCompany_returnsFundedById() {
        UUID funderId = TestIds.uuid(99);
        Company funder = new Company();
        funder.setId(funderId);
        PromotionRule rule = makeRule(RULE_ID);
        rule.setFundedByCompany(funder);
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(rule));

        PromotionRuleResponse result = service.getRule(COMPANY_ID, RULE_ID, OWNER_ID);

        assertEquals(funderId, result.fundedByCompanyId());
    }

    @Test
    void getRule_corruptConfigJson_throwsIllegalState() {
        PromotionRule rule = makeRule(RULE_ID);
        rule.setConfigJson("not valid json {{{");
        when(ruleRepository.findByIdAndCompanyId(RULE_ID, COMPANY_ID)).thenReturn(Optional.of(rule));

        assertThrows(IllegalStateException.class,
                () -> service.getRule(COMPANY_ID, RULE_ID, OWNER_ID));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Company makeCompany() {
        Company c = new Company();
        c.setId(COMPANY_ID);
        c.setName("Test Company");
        return c;
    }

    private Product makeProduct(UUID id) {
        Product p = new Product();
        p.setId(id);
        p.setCompany(makeCompany());
        p.setName("Product");
        p.setPrice(new BigDecimal("10.00"));
        p.setCurrency("USD");
        p.setStatus(ProductStatus.ACTIVE);
        p.setImages(new ArrayList<>());
        p.setOptions(new ArrayList<>());
        p.setVariants(new ArrayList<>());
        p.setAttributes(new ArrayList<>());
        return p;
    }

    private PromotionRule makeRule(UUID id) {
        PromotionRule r = new PromotionRule();
        r.setId(id);
        r.setCompany(makeCompany());
        r.setName("Test Rule");
        r.setRuleType(PromotionRuleType.PERCENTAGE_OFF);
        r.setConfigJson("{}");
        r.setStatus(DiscountStatus.ACTIVE);
        r.setPriority(100);
        r.setTargetProducts(new HashSet<>());
        r.setTargetBundles(new HashSet<>());
        r.setTargetSegments(new HashSet<>());
        return r;
    }

    private CreatePromotionRuleRequest minimalCreateRequest() {
        CreatePromotionRuleRequest req = new CreatePromotionRuleRequest();
        req.setName("Test Rule");
        req.setRuleType("PERCENTAGE_OFF");
        req.setConfig(objectMapper.createObjectNode());
        return req;
    }
}
