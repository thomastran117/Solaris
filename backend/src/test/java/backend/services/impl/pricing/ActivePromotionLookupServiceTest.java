package backend.services.impl.pricing;

import backend.dtos.responses.product.ActivePromotionSummary;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductBundle;
import backend.models.core.PromotionRule;
import backend.models.enums.DiscountStatus;
import backend.models.enums.PromotionRuleType;
import backend.repositories.PromotionRuleRepository;
import backend.services.pricing.config.PromotionConfigValidator;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivePromotionLookupServiceTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID PRODUCT_A = TestIds.uuid(2);
    private static final UUID PRODUCT_B = TestIds.uuid(3);

    private PromotionRuleRepository promotionRuleRepository;
    private ActivePromotionLookupService service;

    @BeforeEach
    void setUp() {
        promotionRuleRepository = mock(PromotionRuleRepository.class);
        service = new ActivePromotionLookupService(
                promotionRuleRepository,
                new PromotionConfigValidator(new ObjectMapper())
        );
    }

    @Test
    void findForProducts_returnsEmptyMapForEmptyInput() {
        assertTrue(service.findForProducts(List.of()).isEmpty());
    }

    @Test
    void findForProducts_wholeCatalogRuleAppliesToAllCompanyProducts() {
        Product first = product(PRODUCT_A);
        Product second = product(PRODUCT_B);
        when(promotionRuleRepository.findActiveCandidates(eq(Set.of(COMPANY_ID)), any()))
                .thenReturn(List.of(percentageRule("15", 100)));

        Map<UUID, ActivePromotionSummary> result = service.findForProducts(List.of(first, second));

        assertEquals(2, result.size());
        assertEquals(new BigDecimal("15"), result.get(PRODUCT_A).percentOff());
        verify(promotionRuleRepository).findActiveCandidates(eq(Set.of(COMPANY_ID)), any());
    }

    @Test
    void findForProducts_skipsBundleScopedRules() {
        Product first = product(PRODUCT_A);
        PromotionRule bundleRule = percentageRule("15", 100);
        ProductBundle bundle = new ProductBundle();
        bundle.setId(TestIds.uuid(30));
        bundleRule.setTargetBundles(new HashSet<>(Set.of(bundle)));
        when(promotionRuleRepository.findActiveCandidates(eq(Set.of(COMPANY_ID)), any()))
                .thenReturn(List.of(bundleRule));

        Map<UUID, ActivePromotionSummary> result = service.findForProducts(List.of(first));

        assertTrue(result.isEmpty());
    }

    @Test
    void findForProducts_prefersLowerPriorityThenGreaterSavingOnTie() {
        Product first = product(PRODUCT_A);
        PromotionRule lowerPrecedence = percentageRule("10", 50);
        lowerPrecedence.setTargetProducts(new HashSet<>(Set.of(first)));
        PromotionRule higherPrecedence = percentageRule("25", 10);
        higherPrecedence.setTargetProducts(new HashSet<>(Set.of(first)));
        when(promotionRuleRepository.findActiveCandidates(eq(Set.of(COMPANY_ID)), any()))
                .thenReturn(List.of(lowerPrecedence, higherPrecedence));

        Map<UUID, ActivePromotionSummary> result = service.findForProducts(List.of(first));

        assertEquals(new BigDecimal("25"), result.get(PRODUCT_A).percentOff());
    }

    @Test
    void findForProducts_tieOnPriorityUsesLargerNominalSaving() {
        Product first = product(PRODUCT_A);
        PromotionRule smaller = fixedRule("5.00", 10);
        smaller.setTargetProducts(new HashSet<>(Set.of(first)));
        PromotionRule larger = fixedRule("8.00", 10);
        larger.setTargetProducts(new HashSet<>(Set.of(first)));
        when(promotionRuleRepository.findActiveCandidates(eq(Set.of(COMPANY_ID)), any()))
                .thenReturn(List.of(smaller, larger));

        Map<UUID, ActivePromotionSummary> result = service.findForProducts(List.of(first));

        assertEquals(0, new BigDecimal("8.00").compareTo(result.get(PRODUCT_A).amountOff()));
    }

    @Test
    void findForProducts_invalidConfigReturnsSummaryWithoutHeadlineDiscountValues() {
        Product first = product(PRODUCT_A);
        PromotionRule broken = percentageRule("15", 10);
        broken.setTargetProducts(new HashSet<>(Set.of(first)));
        broken.setConfigJson("{\"percent\":\"bad\",\"appliesTo\":\"LINE\"}");
        when(promotionRuleRepository.findActiveCandidates(eq(Set.of(COMPANY_ID)), any()))
                .thenReturn(List.of(broken));

        Map<UUID, ActivePromotionSummary> result = service.findForProducts(List.of(first));

        assertEquals(PromotionRuleType.PERCENTAGE_OFF, result.get(PRODUCT_A).ruleType());
        assertNull(result.get(PRODUCT_A).percentOff());
        assertNull(result.get(PRODUCT_A).amountOff());
    }

    @Test
    void findForProducts_nullInput_returnsEmpty() {
        assertTrue(service.findForProducts(null).isEmpty());
    }

    @Test
    void findForProducts_noActiveCandidates_returnsEmpty() {
        when(promotionRuleRepository.findActiveCandidates(any(), any())).thenReturn(List.of());

        Map<UUID, ActivePromotionSummary> result = service.findForProducts(List.of(product(PRODUCT_A)));

        assertTrue(result.isEmpty());
    }

    @Test
    void findForProducts_productWithNullCompany_skipped() {
        Product noCompany = new Product();
        noCompany.setId(TestIds.uuid(99));
        // company is null — should be silently ignored, leading to empty companyIdMap → empty result
        assertTrue(service.findForProducts(List.of(noCompany)).isEmpty());
    }

    @Test
    void findForProducts_bogoRule_summaryHasNullDiscountValues() {
        Product first = product(PRODUCT_A);
        PromotionRule bogoRule = baseRule(PromotionRuleType.BOGO, 10);
        bogoRule.setConfigJson("{\"buyQty\":1,\"getQty\":1,\"maxRedemptions\":0}");

        when(promotionRuleRepository.findActiveCandidates(eq(Set.of(COMPANY_ID)), any()))
                .thenReturn(List.of(bogoRule));

        Map<UUID, ActivePromotionSummary> result = service.findForProducts(List.of(first));

        assertEquals(1, result.size());
        ActivePromotionSummary summary = result.get(PRODUCT_A);
        assertEquals(PromotionRuleType.BOGO, summary.ruleType());
        assertNull(summary.percentOff());
        assertNull(summary.amountOff());
    }

    @Test
    void findForProducts_bogoAndFixedSamePriority_fixedWins() {
        // When tie-breaking: BOGO nominalSaving = 0, FIXED_OFF = 5.00 → fixed wins
        Product first = product(PRODUCT_A);
        PromotionRule bogo = baseRule(PromotionRuleType.BOGO, 10);
        bogo.setConfigJson("{\"buyQty\":1,\"getQty\":1,\"maxRedemptions\":0}");
        bogo.setTargetProducts(new HashSet<>(Set.of(first)));

        PromotionRule fixed = fixedRule("5.00", 10); // same priority
        fixed.setTargetProducts(new HashSet<>(Set.of(first)));

        when(promotionRuleRepository.findActiveCandidates(eq(Set.of(COMPANY_ID)), any()))
                .thenReturn(List.of(bogo, fixed));

        Map<UUID, ActivePromotionSummary> result = service.findForProducts(List.of(first));

        assertEquals(PromotionRuleType.FIXED_OFF, result.get(PRODUCT_A).ruleType());
    }

    private Product product(UUID id) {
        Company company = new Company();
        company.setId(COMPANY_ID);
        Product product = new Product();
        product.setId(id);
        product.setCompany(company);
        return product;
    }

    private PromotionRule percentageRule(String percent, int priority) {
        PromotionRule rule = baseRule(PromotionRuleType.PERCENTAGE_OFF, priority);
        rule.setConfigJson("{\"percent\":" + percent + ",\"appliesTo\":\"LINE\"}");
        return rule;
    }

    private PromotionRule fixedRule(String amount, int priority) {
        PromotionRule rule = baseRule(PromotionRuleType.FIXED_OFF, priority);
        rule.setConfigJson("{\"amount\":" + amount + ",\"appliesTo\":\"LINE\"}");
        return rule;
    }

    private PromotionRule baseRule(PromotionRuleType type, int priority) {
        Company company = new Company();
        company.setId(COMPANY_ID);

        PromotionRule rule = new PromotionRule();
        rule.setId(TestIds.uuid(priority + 100));
        rule.setCompany(company);
        rule.setName("rule-" + priority);
        rule.setRuleType(type);
        rule.setPriority(priority);
        rule.setStatus(DiscountStatus.ACTIVE);
        rule.setTargetProducts(new HashSet<>());
        rule.setTargetBundles(new HashSet<>());
        return rule;
    }
}
