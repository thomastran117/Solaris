package backend.services.impl.pricing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import backend.testutil.TestIds;

import backend.models.core.Company;
import backend.models.core.Coupon;
import backend.models.core.CustomerSegment;
import backend.models.core.Product;
import backend.models.core.ProductBundle;
import backend.models.core.PromotionRule;
import backend.models.enums.DiscountStatus;
import backend.models.enums.DiscountType;
import backend.models.enums.PromotionRuleType;
import backend.repositories.CouponRepository;
import backend.repositories.PromotionPerUserCountRepository;
import backend.repositories.PromotionRuleRepository;
import backend.services.pricing.AppliedPromotion;
import backend.services.pricing.CartContext;
import backend.services.pricing.CartLine;
import backend.services.pricing.LineBreakdown;
import backend.services.pricing.PricingResult;
import backend.services.pricing.ResolvedTaxRate;
import backend.models.enums.TaxSource;
import backend.services.pricing.config.PromotionConfigValidator;
import backend.services.pricing.evaluators.BogoEvaluator;
import backend.services.pricing.evaluators.FixedOffEvaluator;
import backend.services.pricing.evaluators.FreeShippingEvaluator;
import backend.services.pricing.evaluators.PercentageOffEvaluator;
import backend.services.pricing.evaluators.RuleEvaluator;
import backend.services.pricing.evaluators.TieredPriceEvaluator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PricingEngineImplTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID FUNDER_ID  = TestIds.uuid(99);

    private PricingEngineImpl engine;
    private ObjectMapper objectMapper;
    private PromotionConfigValidator configValidator;
    private int ruleCounter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        configValidator = new PromotionConfigValidator(objectMapper);
        List<RuleEvaluator> evaluators = List.of(
                new PercentageOffEvaluator(),
                new FixedOffEvaluator(),
                new BogoEvaluator(),
                new TieredPriceEvaluator(),
                new FreeShippingEvaluator());
        engine = new PricingEngineImpl(
                mock(PromotionRuleRepository.class),
                mock(CouponRepository.class),
                configValidator,
                mock(PromotionPerUserCountRepository.class),
                new TaxServiceImpl(mock(backend.repositories.TaxRateRepository.class), new BigDecimal("0.00"), false),
                evaluators);
        ruleCounter = 1;
    }

    // -------------------- 1. empty cart --------------------

    @Test
    void emptyCart_returnsZeroTotals() {
        CartContext ctx = new CartContext(List.of(), null, Set.of(), "USD", null, null, Instant.now());
        PricingResult r = engine.quote(ctx);
        assertEquals(bd("0.00"), r.subtotal());
        assertEquals(bd("0.00"), r.finalTotal());
        assertTrue(r.appliedPromotions().isEmpty());
    }

    // -------------------- 2. no applicable rules --------------------

    @Test
    void noRules_finalTotalEqualsSubtotal() {
        CartContext ctx = context(List.of(line(0, 1, 2, "10.00")), null, Set.of());
        PricingResult r = engine.compute(ctx, List.of(), null);
        assertEquals(bd("20.00"), r.subtotal());
        assertEquals(bd("20.00"), r.finalTotal());
        assertEquals(bd("0.00"), r.promotionSavings());
    }

    // -------------------- 3. percentage LINE scope rounding --------------------

    @Test
    void percentageLine_roundsHalfUp() {
        CartContext ctx = context(List.of(line(0, 1, 1, "33.33")), null, Set.of());
        PromotionRule r = percentageRule("10", null, "LINE", false, 100);
        PricingResult result = engine.compute(ctx, List.of(r), null);
        assertEquals(bd("3.33"), result.promotionSavings());
        assertEquals(bd("30.00"), result.finalTotal());
    }

    // -------------------- 4. fixed ORDER scope caps at subtotal --------------------

    @Test
    void fixedOrder_capsAtSubtotal() {
        CartContext ctx = context(List.of(line(0, 1, 1, "5.00")), null, Set.of());
        PromotionRule r = fixedRule("50.00", "ORDER", false, 100);
        PricingResult result = engine.compute(ctx, List.of(r), null);
        assertEquals(bd("5.00"), result.promotionSavings());
        assertEquals(bd("0.00"), result.finalTotal());
    }

    // -------------------- 5. non-stackable: lowest priority wins --------------------

    @Test
    void nonStackable_lowestPriorityWinsOthersSkipped() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        PromotionRule winner = percentageRule("20", null, "ORDER", false, 10);
        PromotionRule loser  = percentageRule("50", null, "ORDER", false, 20);
        PricingResult result = engine.compute(ctx, List.of(winner, loser), null);
        assertEquals(1, result.appliedPromotions().size());
        assertEquals(winner.getId(), result.appliedPromotions().get(0).ruleId());
        assertEquals(bd("20.00"), result.promotionSavings());
        assertEquals(bd("80.00"), result.finalTotal());
    }

    // -------------------- 6. stackable cascade --------------------

    @Test
    void stackableCascade_appliesOnReducedAmount() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        PromotionRule ten  = percentageRule("10", null, "ORDER", true, 10);
        PromotionRule five = fixedRule("5.00", "ORDER", true, 20);
        PricingResult result = engine.compute(ctx, List.of(ten, five), null);
        assertEquals(bd("15.00"), result.promotionSavings());
        assertEquals(bd("85.00"), result.finalTotal());
        assertEquals(2, result.appliedPromotions().size());
    }

    // -------------------- 7. BOGO buy-2-get-1-free same SKU --------------------

    @Test
    void bogo_buyTwoGetOneFree_sameSku() {
        CartContext ctx = context(List.of(line(0, 1, 3, "10.00")), null, Set.of());
        PromotionRule rule = bogoRule(List.of(TestIds.uuid(1)), 2, List.of(), 1, "100", 10);
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(bd("10.00"), result.promotionSavings());
        assertEquals(bd("20.00"), result.finalTotal());
    }

    // -------------------- 8. BOGO max applications cap --------------------

    @Test
    void bogo_maxApplicationsCap() {
        CartContext ctx = context(List.of(line(0, 1, 10, "10.00")), null, Set.of());
        PromotionRule rule = bogoRule(List.of(TestIds.uuid(1)), 1, List.of(), 1, "100", 2);
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(bd("20.00"), result.promotionSavings());
    }

    // -------------------- 9. BOGO reward from cheapest line first --------------------

    @Test
    void bogo_rewardCheapestLineFirst() {
        CartContext ctx = context(List.of(
                line(0, 1, 1, "100.00"),
                line(1, 2, 1, "20.00"),
                line(2, 3, 1, "30.00")
        ), null, Set.of());
        PromotionRule rule = bogoRule(List.of(TestIds.uuid(1)), 1, List.of(TestIds.uuid(2), TestIds.uuid(3)), 1, "100", 1);
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(bd("20.00"), result.promotionSavings());
        LineBreakdown p2 = result.lines().stream()
                .filter(l -> TestIds.uuid(2).equals(l.productId())).findFirst().orElseThrow();
        assertEquals(bd("20.00"), p2.savings());
    }

    // -------------------- 10. tiered pricing at breakpoints --------------------

    @Test
    void tieredPrice_selectsHighestBreakpoint() {
        CartContext ctx = context(List.of(line(0, 1, 5, "10.00")), null, Set.of());
        PromotionRule rule = tieredRule(List.of(bp(1, "10"), bp(5, "9"), bp(10, "8")));
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(bd("5.00"), result.promotionSavings());
        assertEquals(bd("45.00"), result.finalTotal());
    }

    // -------------------- 11. tiered inapplicable when base <= tier --------------------

    @Test
    void tieredPrice_inapplicableWhenBaseLowerThanTier() {
        CartContext ctx = context(List.of(line(0, 1, 5, "8.00")), null, Set.of());
        PromotionRule rule = tieredRule(List.of(bp(1, "10"), bp(5, "9")));
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(bd("0.00"), result.promotionSavings());
        assertEquals(bd("40.00"), result.finalTotal());
    }

    // -------------------- 12. segment gate --------------------

    @Test
    void segmentGate_skipsNonMember() {
        CartContext anon  = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        CartContext other = context(List.of(line(0, 1, 1, "100.00")), 42L, Set.of(999L));
        CartContext vip   = context(List.of(line(0, 1, 1, "100.00")), 42L, Set.of(55L));

        PromotionRule rule = percentageRule("10", null, "ORDER", false, 100);
        CustomerSegment seg = new CustomerSegment();
        seg.setId(TestIds.uuid(55));
        seg.setCode("VIP");
        rule.setTargetSegments(new HashSet<>(List.of(seg)));

        assertEquals(bd("0.00"), engine.compute(anon,  List.of(rule), null).promotionSavings());
        assertEquals(bd("0.00"), engine.compute(other, List.of(rule), null).promotionSavings());
        assertEquals(bd("10.00"), engine.compute(vip, List.of(rule), null).promotionSavings());
    }

    // -------------------- 13. minCartAmount --------------------

    @Test
    void minCartAmount_skipsBelowThresholdWithWarning() {
        CartContext ctx = context(List.of(line(0, 1, 1, "5.00")), null, Set.of());
        PromotionRule rule = percentageRule("50", null, "ORDER", false, 100);
        rule.setMinCartAmount(bd("10.00"));
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(bd("0.00"), result.promotionSavings());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("minCartAmount")));
    }

    // -------------------- 14. product target gate --------------------

    @Test
    void productTargetGate_skipsLinesNotInSet() {
        CartContext ctx = context(List.of(
                line(0, 1, 1, "10.00"),
                line(1, 2, 1, "20.00")
        ), null, Set.of());
        PromotionRule rule = percentageRule("50", null, "LINE", false, 100);
        Product p2 = new Product();
        p2.setId(TestIds.uuid(2));
        rule.setTargetProducts(new HashSet<>(List.of(p2)));
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(bd("10.00"), result.promotionSavings());
    }

    // -------------------- 15. coupon stacks on post-rule subtotal --------------------

    @Test
    void coupon_stacksOnPostPromotionSubtotal() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        PromotionRule rule = percentageRule("10", null, "ORDER", false, 100);
        Coupon c = coupon("SAVE10", DiscountType.PERCENTAGE, "10.00", null);
        PricingResult result = engine.compute(ctx, List.of(rule), c);
        assertEquals(bd("10.00"), result.promotionSavings());
        assertEquals(bd("9.00"), result.couponSavings());
        assertEquals(bd("81.00"), result.finalTotal());
        assertEquals("SAVE10", result.appliedCouponCode());
    }

    // -------------------- 16. coupon minOrderAmount checked post-rule --------------------

    @Test
    void coupon_minOrderAmountAgainstPostPromotionSubtotal() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        PromotionRule rule = percentageRule("50", null, "ORDER", false, 100);
        Coupon c = coupon("BIG", DiscountType.PERCENTAGE, "10.00", "60.00");
        PricingResult result = engine.compute(ctx, List.of(rule), c);
        assertEquals(bd("0.00"), result.couponSavings());
        assertNull(result.appliedCouponCode());
    }

    // -------------------- 17. vendor-funded attribution --------------------

    @Test
    void vendorFunded_attributionPropagated() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        PromotionRule rule = percentageRule("10", null, "ORDER", false, 100);
        Company funder = new Company();
        funder.setId(FUNDER_ID);
        rule.setFundedByCompany(funder);
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        AppliedPromotion ap = result.appliedPromotions().get(0);
        assertEquals(FUNDER_ID, ap.fundedByCompanyId());
    }

    @Test
    void fundedByCompanyDefaultsToOwningCompany() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        PromotionRule rule = percentageRule("10", null, "ORDER", false, 100);
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(COMPANY_ID, result.appliedPromotions().get(0).fundedByCompanyId());
    }

    // -------------------- 18. multiple rules → breakdown correct --------------------

    @Test
    void multipleStackableRules_perLineBreakdown() {
        CartContext ctx = context(List.of(
                line(0, 1, 2, "10.00"),
                line(1, 2, 1, "30.00")
        ), null, Set.of());
        PromotionRule r1 = percentageRule("10", null, "LINE", true, 10);
        PromotionRule r2 = fixedRule("5.00", "LINE", true, 20);
        PricingResult result = engine.compute(ctx, List.of(r1, r2), null);

        LineBreakdown l0 = result.lines().get(0);
        assertEquals(bd("7.00"), l0.savings());
        assertEquals(bd("13.00"), l0.effectiveLineTotal());
        assertEquals(2, l0.appliedRuleIds().size());

        LineBreakdown l1 = result.lines().get(1);
        assertEquals(bd("8.00"), l1.savings());
        assertEquals(bd("22.00"), l1.effectiveLineTotal());
    }

    // -------------------- 19. rounding sum invariant --------------------

    @Test
    void roundingInvariant_lineSumsEqualFinal() {
        CartContext ctx = context(List.of(
                line(0, 1, 1, "7.77"),
                line(1, 2, 1, "3.33"),
                line(2, 3, 1, "11.11")
        ), null, Set.of());
        PromotionRule rule = percentageRule("15", null, "ORDER", false, 100);
        PricingResult result = engine.compute(ctx, List.of(rule), null);

        BigDecimal sumOfLines = result.lines().stream()
                .map(LineBreakdown::effectiveLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(result.finalTotal(), sumOfLines);
    }

    // -------------------- 20. inactive/expired rules filtered --------------------

    @Test
    void inactiveRule_isSkipped() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        PromotionRule rule = percentageRule("10", null, "ORDER", false, 100);
        rule.setStatus(DiscountStatus.DISABLED);
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(bd("0.00"), result.promotionSavings());
    }

    @Test
    void timeWindow_skipsFutureStart() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        PromotionRule rule = percentageRule("10", null, "ORDER", false, 100);
        rule.setStartDate(ctx.now().plusSeconds(3600));
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(bd("0.00"), result.promotionSavings());
    }

    @Test
    void timeWindow_skipsPastEnd() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        PromotionRule rule = percentageRule("10", null, "ORDER", false, 100);
        rule.setEndDate(ctx.now().minusSeconds(3600));
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(bd("0.00"), result.promotionSavings());
    }

    // -------------------- 21. FREE_SHIPPING --------------------

    @Test
    void freeShipping_reducesShippingToZero() {
        CartContext ctx = contextWithShipping(
                List.of(line(0, 1, 1, "50.00")), null, Set.of(), "12.50");
        PromotionRule rule = freeShippingRule(null, false);
        PricingResult result = engine.compute(ctx, List.of(rule), null);

        assertEquals(bd("0.00"), result.promotionSavings());
        assertEquals(bd("12.50"), result.shippingSavings());
        assertEquals(bd("0.00"), result.shippingAmount());
        assertEquals(bd("50.00"), result.finalTotal());
        assertEquals(1, result.appliedPromotions().size());
        assertEquals(bd("12.50"), result.appliedPromotions().get(0).savings());
    }

    @Test
    void freeShipping_cappedByMaxShippingDiscount() {
        CartContext ctx = contextWithShipping(
                List.of(line(0, 1, 1, "50.00")), null, Set.of(), "20.00");
        PromotionRule rule = freeShippingRule("5.00", false);
        PricingResult result = engine.compute(ctx, List.of(rule), null);

        assertEquals(bd("5.00"), result.shippingSavings());
        assertEquals(bd("15.00"), result.shippingAmount());
        assertEquals(bd("65.00"), result.finalTotal());
    }

    @Test
    void freeShipping_requiresAllTargetProducts_skipsWhenMissing() {
        CartContext ctx = contextWithShipping(
                List.of(line(0, 1, 1, "50.00")), null, Set.of(), "10.00");
        PromotionRule rule = freeShippingRule(null, true);
        Product p1 = new Product();
        p1.setId(TestIds.uuid(1));
        Product p2 = new Product();
        p2.setId(TestIds.uuid(2));
        rule.setTargetProducts(new HashSet<>(List.of(p1, p2)));
        PricingResult result = engine.compute(ctx, List.of(rule), null);

        assertEquals(bd("0.00"), result.shippingSavings());
        assertEquals(bd("10.00"), result.shippingAmount());
        assertTrue(result.appliedPromotions().isEmpty());
    }

    // -------------------- helpers --------------------

    private static BigDecimal bd(String v) {
        return new BigDecimal(v).setScale(2);
    }

    private static CartLine line(int index, long productIdSeed, int qty, String unitPrice) {
        return new CartLine(index, TestIds.uuid(productIdSeed), null, qty, new BigDecimal(unitPrice), COMPANY_ID, null);
    }

    private static CartLine bundleLine(int index, long bundleIdSeed, int qty, String unitPrice) {
        return new CartLine(index, null, null, qty, new BigDecimal(unitPrice), COMPANY_ID, TestIds.uuid(bundleIdSeed));
    }

    private static CartContext context(List<CartLine> lines, Long userIdSeed, Set<Long> segmentSeeds) {
        UUID userId = userIdSeed == null ? null : TestIds.uuid(userIdSeed);
        Set<UUID> segments = segmentSeeds.stream().map(TestIds::uuid).collect(Collectors.toSet());
        return new CartContext(lines, userId, segments, "USD", null, null, Instant.now());
    }

    private static CartContext contextWithShipping(
            List<CartLine> lines, Long userIdSeed, Set<Long> segmentSeeds, String shipping) {
        UUID userId = userIdSeed == null ? null : TestIds.uuid(userIdSeed);
        Set<UUID> segments = segmentSeeds.stream().map(TestIds::uuid).collect(Collectors.toSet());
        return new CartContext(lines, userId, segments, "USD", null, new BigDecimal(shipping), Instant.now());
    }

    private PromotionRule freeShippingRule(String maxShippingDiscount, boolean requiresAll) {
        String json = "{"
                + (maxShippingDiscount != null ? "\"maxShippingDiscount\":" + maxShippingDiscount + "," : "")
                + "\"requiresAllTargetProducts\":" + requiresAll
                + "}";
        return baseRule(PromotionRuleType.FREE_SHIPPING, json, false, 100);
    }

    private PromotionRule baseRule(PromotionRuleType type, String configJson, boolean stackable, int priority) {
        PromotionRule r = new PromotionRule();
        r.setId(TestIds.uuid(ruleCounter++));
        Company c = new Company();
        c.setId(COMPANY_ID);
        r.setCompany(c);
        r.setName("rule-" + r.getId());
        r.setRuleType(type);
        r.setConfigJson(configJson);
        r.setStatus(DiscountStatus.ACTIVE);
        r.setStackable(stackable);
        r.setPriority(priority);
        r.setTargetProducts(new HashSet<>());
        r.setTargetBundles(new HashSet<>());
        r.setTargetSegments(new HashSet<>());
        return r;
    }

    private PromotionRule percentageRule(String pct, String maxDiscount, String scope, boolean stackable, int priority) {
        String json = "{\"percent\":" + pct
                + (maxDiscount != null ? ",\"maxDiscount\":" + maxDiscount : "")
                + ",\"appliesTo\":\"" + scope + "\"}";
        return baseRule(PromotionRuleType.PERCENTAGE_OFF, json, stackable, priority);
    }

    private PromotionRule fixedRule(String amount, String scope, boolean stackable, int priority) {
        String json = "{\"amount\":" + amount + ",\"appliesTo\":\"" + scope + "\"}";
        return baseRule(PromotionRuleType.FIXED_OFF, json, stackable, priority);
    }

    private PromotionRule bogoRule(List<UUID> triggerIds, int triggerQty,
                                   List<UUID> rewardIds, int rewardQty,
                                   String rewardPercent, int maxApps) {
        String triggerArr = uuidsToJson(triggerIds);
        String rewardArr  = uuidsToJson(rewardIds);
        String json = "{\"triggerProductIds\":" + triggerArr
                + ",\"triggerQty\":" + triggerQty
                + ",\"rewardProductIds\":" + rewardArr
                + ",\"rewardQty\":" + rewardQty
                + ",\"rewardPercentOff\":" + rewardPercent
                + ",\"maxApplicationsPerOrder\":" + maxApps + "}";
        return baseRule(PromotionRuleType.BOGO, json, false, 100);
    }

    private static String uuidsToJson(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return "[]";
        return "[" + ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",")) + "]";
    }

    private PromotionRule tieredRule(List<String[]> breakpoints) {
        StringBuilder sb = new StringBuilder("{\"breakpoints\":[");
        for (int i = 0; i < breakpoints.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"minQty\":").append(breakpoints.get(i)[0])
              .append(",\"unitPrice\":").append(breakpoints.get(i)[1]).append("}");
        }
        sb.append("]}");
        return baseRule(PromotionRuleType.TIERED_PRICE, sb.toString(), false, 100);
    }

    private static String[] bp(int minQty, String unitPrice) {
        return new String[]{String.valueOf(minQty), unitPrice};
    }

    private Coupon coupon(String code, DiscountType type, String value, String minOrder) {
        Coupon c = new Coupon();
        c.setId(TestIds.uuid(1));
        c.setCode(code.toUpperCase());
        c.setType(type);
        c.setValue(bd(value));
        c.setStatus(DiscountStatus.ACTIVE);
        if (minOrder != null) c.setMinOrderAmount(bd(minOrder));
        Company co = new Company();
        co.setId(COMPANY_ID);
        c.setCompany(co);
        return c;
    }

    // -------------------- bundle-scoped rule tests --------------------

    @Test
    void bundleRule_firesOnBundleLine() {
        CartLine bl = bundleLine(0, 42, 1, "200.00");
        CartContext ctx = context(List.of(bl), null, Set.of());

        PromotionRule rule = percentageRule("10", null, "LINE", true, 50);
        ProductBundle bundle = new ProductBundle();
        bundle.setId(TestIds.uuid(42));
        rule.setTargetBundles(new HashSet<>(Set.of(bundle)));

        PricingResult result = engine.compute(ctx, List.of(rule), null);

        assertEquals(bd("20.00"), result.promotionSavings());
        assertEquals(bd("180.00"), result.finalTotal());
    }

    @Test
    void bundleRule_doesNotFireOnSameProductsBoughtSeparately() {
        CartLine productLine = line(0, 100, 1, "200.00");
        CartContext ctx = context(List.of(productLine), null, Set.of());

        PromotionRule rule = percentageRule("10", null, "LINE", true, 50);
        ProductBundle bundle = new ProductBundle();
        bundle.setId(TestIds.uuid(42));
        rule.setTargetBundles(new HashSet<>(Set.of(bundle)));

        PricingResult result = engine.compute(ctx, List.of(rule), null);

        assertEquals(bd("0.00"), result.promotionSavings());
        assertEquals(bd("200.00"), result.finalTotal());
    }

    @Test
    void ruleWithBothTargets_matchesUnion() {
        CartLine bl = bundleLine(0, 42, 1, "100.00");
        CartLine pl = line(1, 77, 1, "50.00");
        CartContext ctx = context(List.of(bl, pl), null, Set.of());

        PromotionRule rule = percentageRule("20", null, "LINE", true, 50);
        ProductBundle bundle = new ProductBundle();
        bundle.setId(TestIds.uuid(42));
        rule.setTargetBundles(new HashSet<>(Set.of(bundle)));
        Product p = new Product();
        p.setId(TestIds.uuid(77));
        rule.setTargetProducts(new HashSet<>(Set.of(p)));

        PricingResult result = engine.compute(ctx, List.of(rule), null);

        assertEquals(bd("30.00"), result.promotionSavings());
    }

    @Test
    void existingRules_withEmptyTargetBundles_behavesUnchanged() {
        CartLine pl = line(0, 1, 2, "50.00");
        CartContext ctx = context(List.of(pl), null, Set.of());

        PromotionRule rule = percentageRule("10", null, "LINE", true, 50);

        PricingResult result = engine.compute(ctx, List.of(rule), null);

        assertEquals(bd("10.00"), result.promotionSavings());
        assertEquals(bd("90.00"), result.finalTotal());
    }

    @Test
    void bundleRuleDoesNotTouchUnrelatedProductLines() {
        CartLine bl = bundleLine(0, 42, 1, "200.00");
        CartLine pl = line(1, 99, 1, "100.00");

        CartContext ctx = context(List.of(bl, pl), null, Set.of());

        PromotionRule bundleRule = percentageRule("10", null, "LINE", true, 50);
        ProductBundle bundle = new ProductBundle();
        bundle.setId(TestIds.uuid(42));
        bundleRule.setTargetBundles(new HashSet<>(Set.of(bundle)));

        PricingResult result = engine.compute(ctx, List.of(bundleRule), null);

        assertEquals(bd("20.00"), result.promotionSavings());
        LineBreakdown productBreakdown = result.lines().stream()
                .filter(l -> TestIds.uuid(99).equals(l.productId()))
                .findFirst().orElseThrow();
        assertEquals(bd("0.00"), productBreakdown.savings());
    }

    // -------------------- coupon validation edge cases --------------------

    @Test
    void coupon_inactiveStatus_isSkippedWithWarning() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        Coupon c = coupon("OFF10", DiscountType.PERCENTAGE, "10.00", null);
        c.setStatus(backend.models.enums.DiscountStatus.DISABLED);
        PricingResult result = engine.compute(ctx, List.of(), c);
        assertEquals(bd("0.00"), result.couponSavings());
        assertNull(result.appliedCouponCode());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("not active")));
    }

    @Test
    void coupon_notYetActive_isSkippedWithWarning() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        Coupon c = coupon("FUTURE", DiscountType.PERCENTAGE, "10.00", null);
        c.setStartDate(ctx.now().plusSeconds(3600)); // starts in the future
        PricingResult result = engine.compute(ctx, List.of(), c);
        assertEquals(bd("0.00"), result.couponSavings());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("not yet active")));
    }

    @Test
    void coupon_expired_isSkippedWithWarning() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        Coupon c = coupon("OLD", DiscountType.PERCENTAGE, "10.00", null);
        c.setEndDate(ctx.now().minusSeconds(3600)); // ended in the past
        PricingResult result = engine.compute(ctx, List.of(), c);
        assertEquals(bd("0.00"), result.couponSavings());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("expired")));
    }

    @Test
    void coupon_maxUsesReached_isSkippedWithWarning() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        Coupon c = coupon("USED", DiscountType.PERCENTAGE, "10.00", null);
        c.setMaxUses(5);
        c.setUsedCount(5);
        PricingResult result = engine.compute(ctx, List.of(), c);
        assertEquals(bd("0.00"), result.couponSavings());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("redemption limit")));
    }

    @Test
    void coupon_fixedAmountType_appliesCorrectly() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        Coupon c = coupon("FIXED20", DiscountType.FIXED_AMOUNT, "20.00", null);
        PricingResult result = engine.compute(ctx, List.of(), c);
        assertEquals(bd("20.00"), result.couponSavings());
        assertEquals(bd("80.00"), result.finalTotal());
        assertEquals("FIXED20", result.appliedCouponCode());
    }

    @Test
    void coupon_fixedAmountExceedsSubtotal_capsAtSubtotal() {
        CartContext ctx = context(List.of(line(0, 1, 1, "10.00")), null, Set.of());
        Coupon c = coupon("BIG", DiscountType.FIXED_AMOUNT, "50.00", null);
        PricingResult result = engine.compute(ctx, List.of(), c);
        assertEquals(bd("10.00"), result.couponSavings());
        assertEquals(bd("0.00"), result.finalTotal());
    }

    // -------------------- per-user rule limit — null userId skips repo call --------------------

    @Test
    void perUserRuleLimit_withNullUserId_ruleStillApplies() {
        // maxUsesPerUser set but ctx has no userId → short-circuits, repo never called, rule applies
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        PromotionRule rule = percentageRule("10", null, "ORDER", false, 100);
        rule.setMaxUsesPerUser(1);
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(bd("10.00"), result.promotionSavings());
        assertEquals(bd("90.00"), result.finalTotal());
    }

    // -------------------- emptyResult with shipping --------------------

    @Test
    void emptyCart_withShipping_shippingPassesThrough() {
        CartContext ctx = contextWithShipping(List.of(), null, Set.of(), "5.99");
        PricingResult r = engine.quote(ctx);
        assertEquals(bd("0.00"), r.subtotal());
        assertEquals(bd("5.99"), r.shippingAmount());
        assertEquals(bd("5.99"), r.finalTotal());
    }

    // -------------------- sales tax --------------------

    private static ResolvedTaxRate rate(String r, boolean shippingTaxable, TaxSource source) {
        return new ResolvedTaxRate(new BigDecimal(r), shippingTaxable, source, null);
    }

    @Test
    void tax_appliedOnSubtotal_addedToFinalTotal() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        PricingResult r = engine.compute(ctx, List.of(), null, rate("0.10", false, TaxSource.STATE_DEFAULT));
        assertEquals(bd("100.00"), r.taxableAmount());
        assertEquals(bd("10.00"), r.taxAmount());
        assertEquals(TaxSource.STATE_DEFAULT, r.taxSource());
        assertEquals(bd("110.00"), r.finalTotal());
    }

    @Test
    void tax_appliedOnPostPromotionPostCouponSubtotal() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        PromotionRule promo = percentageRule("10", null, "ORDER", false, 100);
        Coupon c = coupon("SAVE10", DiscountType.PERCENTAGE, "10.00", null);
        // subtotal 100 → -10 promo → 90 → -9 coupon → 81 taxable → tax 8.10 → final 89.10
        PricingResult r = engine.compute(ctx, List.of(promo), c, rate("0.10", false, TaxSource.STATE_DEFAULT));
        assertEquals(bd("81.00"), r.taxableAmount());
        assertEquals(bd("8.10"), r.taxAmount());
        assertEquals(bd("89.10"), r.finalTotal());
    }

    @Test
    void tax_shippingTaxedOnlyWhenShippingTaxable() {
        CartContext taxed = contextWithShipping(List.of(line(0, 1, 1, "50.00")), null, Set.of(), "10.00");
        PricingResult withShipTax = engine.compute(taxed, List.of(), null, rate("0.10", true, TaxSource.STATE_DEFAULT));
        assertEquals(bd("60.00"), withShipTax.taxableAmount());
        assertEquals(bd("6.00"), withShipTax.taxAmount());
        assertEquals(bd("66.00"), withShipTax.finalTotal());

        PricingResult noShipTax = engine.compute(taxed, List.of(), null, rate("0.10", false, TaxSource.STATE_DEFAULT));
        assertEquals(bd("50.00"), noShipTax.taxableAmount());
        assertEquals(bd("5.00"), noShipTax.taxAmount());
        // shipping still charged, just not taxed: 50 + 10 + 5
        assertEquals(bd("65.00"), noShipTax.finalTotal());
    }

    @Test
    void tax_noDestination_quoteReturnsZeroTaxAndNoneSource() {
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        PricingResult r = engine.quote(ctx); // ctx.destination() == null
        assertEquals(bd("0.00"), r.taxAmount());
        assertEquals(TaxSource.NONE, r.taxSource());
        assertEquals(bd("100.00"), r.finalTotal());
    }

    @Test
    void tax_roundsHalfUp() {
        CartContext ctx = context(List.of(line(0, 1, 1, "33.33")), null, Set.of());
        // 33.33 * 0.08875 = 2.9580... → 2.96
        PricingResult r = engine.compute(ctx, List.of(), null, rate("0.08875", false, TaxSource.DESTINATION_MATCH));
        assertEquals(bd("2.96"), r.taxAmount());
        assertEquals(bd("36.29"), r.finalTotal());
    }
}
