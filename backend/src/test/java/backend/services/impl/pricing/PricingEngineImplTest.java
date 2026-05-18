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
import backend.repositories.PromotionRuleRepository;
import backend.services.pricing.AppliedPromotion;
import backend.services.pricing.CartContext;
import backend.services.pricing.CartLine;
import backend.services.pricing.LineBreakdown;
import backend.services.pricing.PricingResult;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Pure-Java unit tests for PricingEngineImpl. No Spring context — repositories are mocked
 * (never invoked by {@code compute()}, which is the entry point under test) and evaluators
 * are constructed directly.
 */
class PricingEngineImplTest {

    private static final long COMPANY_ID = 1L;
    private static final long FUNDER_ID  = 99L;

    private PricingEngineImpl engine;
    private ObjectMapper objectMapper;
    private PromotionConfigValidator configValidator;
    private long nextRuleId;

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
                evaluators);
        nextRuleId = 1L;
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
        // 10% of 33.33 = 3.333 → HALF_UP → 3.33
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
        // 100 → 10% off → 90 → $5 off → 85
        CartContext ctx = context(List.of(line(0, 1, 1, "100.00")), null, Set.of());
        PromotionRule ten = percentageRule("10", null, "ORDER", true, 10);
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
        PromotionRule rule = bogoRule(List.of(1L), 2, List.of(), 1, "100", 10);
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(bd("10.00"), result.promotionSavings());
        assertEquals(bd("20.00"), result.finalTotal());
    }

    // -------------------- 8. BOGO max applications cap --------------------

    @Test
    void bogo_maxApplicationsCap() {
        // Cart of 10 shoes, triggerQty=1, rewardQty=1 → would fire 5 times, capped at 2
        CartContext ctx = context(List.of(line(0, 1, 10, "10.00")), null, Set.of());
        PromotionRule rule = bogoRule(List.of(1L), 1, List.of(), 1, "100", 2);
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(bd("20.00"), result.promotionSavings());
    }

    // -------------------- 9. BOGO reward from cheapest line first --------------------

    @Test
    void bogo_rewardCheapestLineFirst() {
        // Two products both in reward set; reward should be taken from the cheapest
        CartContext ctx = context(List.of(
                line(0, 1, 1, "100.00"),
                line(1, 2, 1, "20.00"),
                line(2, 3, 1, "30.00")
        ), null, Set.of());
        // trigger = product 1, reward = product 2 or 3; 1 application → 1 free reward unit from cheapest
        PromotionRule rule = bogoRule(List.of(1L), 1, List.of(2L, 3L), 1, "100", 1);
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(bd("20.00"), result.promotionSavings()); // product 2 (cheapest)
        // line for productId=2 should have savings == 20.00
        LineBreakdown p2 = result.lines().stream().filter(l -> Long.valueOf(2L).equals(l.productId())).findFirst().orElseThrow();
        assertEquals(bd("20.00"), p2.savings());
    }

    // -------------------- 10. tiered pricing at breakpoints --------------------

    @Test
    void tieredPrice_selectsHighestBreakpoint() {
        // Breakpoints: 1:10, 5:9, 10:8. Cart qty 5 → per-unit 9 → saving = (10-9)*5 = 5
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
        p2.setId(2L);
        rule.setTargetProducts(new HashSet<>(List.of(p2)));
        PricingResult result = engine.compute(ctx, List.of(rule), null);
        assertEquals(bd("10.00"), result.promotionSavings()); // 50% of 20.00 only
    }

    // -------------------- 15. coupon stacks on post-rule subtotal --------------------

    @Test
    void coupon_stacksOnPostPromotionSubtotal() {
        // Base 100 → 10% rule → 90 → 10% coupon → 9 → final 81
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
        // Base 100 → 50% rule → 50. Coupon needs min 60 → rejected.
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

        // Line 0: 20.00 → 10% = 2.00 → remaining 18.00 → $5 = 5.00 → savings 7.00, remaining 13.00
        LineBreakdown l0 = result.lines().get(0);
        assertEquals(bd("7.00"), l0.savings());
        assertEquals(bd("13.00"), l0.effectiveLineTotal());
        assertEquals(2, l0.appliedRuleIds().size());

        // Line 1: 30.00 → 10% = 3.00 → remaining 27.00 → $5 = 5.00 → savings 8.00, remaining 22.00
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
        p1.setId(1L);
        Product p2 = new Product();
        p2.setId(2L);
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

    private static CartLine line(int index, long productId, int qty, String unitPrice) {
        return new CartLine(index, productId, null, qty, new BigDecimal(unitPrice), COMPANY_ID, null);
    }

    private static CartLine bundleLine(int index, long bundleId, int qty, String unitPrice) {
        return new CartLine(index, null, null, qty, new BigDecimal(unitPrice), COMPANY_ID, bundleId);
    }

    private static CartContext context(List<CartLine> lines, Long userId, Set<Long> segments) {
        return new CartContext(lines, userId, segments, "USD", null, null, Instant.now());
    }

    private static CartContext contextWithShipping(
            List<CartLine> lines, Long userId, Set<Long> segments, String shipping) {
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
        r.setId(nextRuleId++);
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

    private PromotionRule bogoRule(List<Long> triggerIds, int triggerQty,
                                   List<Long> rewardIds, int rewardQty,
                                   String rewardPercent, int maxApps) {
        String triggerArr = listToJson(triggerIds);
        String rewardArr  = listToJson(rewardIds);
        String json = "{\"triggerProductIds\":" + triggerArr
                + ",\"triggerQty\":" + triggerQty
                + ",\"rewardProductIds\":" + rewardArr
                + ",\"rewardQty\":" + rewardQty
                + ",\"rewardPercentOff\":" + rewardPercent
                + ",\"maxApplicationsPerOrder\":" + maxApps + "}";
        return baseRule(PromotionRuleType.BOGO, json, false, 100);
    }

    private static String listToJson(List<Long> ids) {
        if (ids == null) return "[]";
        return "[" + String.join(",", ids.stream().map(String::valueOf).toList()) + "]";
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
        c.setId(1L);
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
        long bundleId = 42L;
        CartLine bl = bundleLine(0, bundleId, 1, "200.00");
        CartContext ctx = context(List.of(bl), null, Set.of());

        PromotionRule rule = percentageRule("10", null, "LINE", true, 50);
        ProductBundle bundle = new ProductBundle();
        bundle.setId(bundleId);
        rule.setTargetBundles(new HashSet<>(Set.of(bundle)));

        PricingResult result = engine.compute(ctx, List.of(rule), null);

        assertEquals(bd("20.00"), result.promotionSavings());
        assertEquals(bd("180.00"), result.finalTotal());
    }

    @Test
    void bundleRule_doesNotFireOnSameProductsBoughtSeparately() {
        long bundleId = 42L;
        // same products as if the bundle were expanded, but no bundleId set
        CartLine productLine = line(0, 100L, 1, "200.00");
        CartContext ctx = context(List.of(productLine), null, Set.of());

        PromotionRule rule = percentageRule("10", null, "LINE", true, 50);
        ProductBundle bundle = new ProductBundle();
        bundle.setId(bundleId);
        rule.setTargetBundles(new HashSet<>(Set.of(bundle)));

        PricingResult result = engine.compute(ctx, List.of(rule), null);

        assertEquals(bd("0.00"), result.promotionSavings());
        assertEquals(bd("200.00"), result.finalTotal());
    }

    @Test
    void ruleWithBothTargets_matchesUnion() {
        long bundleId = 42L;
        long productId = 77L;

        CartLine bl = bundleLine(0, bundleId, 1, "100.00");
        CartLine pl = line(1, productId, 1, "50.00");
        CartContext ctx = context(List.of(bl, pl), null, Set.of());

        PromotionRule rule = percentageRule("20", null, "LINE", true, 50);
        ProductBundle bundle = new ProductBundle();
        bundle.setId(bundleId);
        rule.setTargetBundles(new HashSet<>(Set.of(bundle)));
        Product p = new Product();
        p.setId(productId);
        rule.setTargetProducts(new HashSet<>(Set.of(p)));

        PricingResult result = engine.compute(ctx, List.of(rule), null);

        // 20% of 100 + 20% of 50 = 30
        assertEquals(bd("30.00"), result.promotionSavings());
    }

    @Test
    void existingRules_withEmptyTargetBundles_behavesUnchanged() {
        CartLine pl = line(0, 1L, 2, "50.00");
        CartContext ctx = context(List.of(pl), null, Set.of());

        PromotionRule rule = percentageRule("10", null, "LINE", true, 50);
        // targetBundles is empty (set by baseRule) — applies to entire catalogue

        PricingResult result = engine.compute(ctx, List.of(rule), null);

        assertEquals(bd("10.00"), result.promotionSavings());
        assertEquals(bd("90.00"), result.finalTotal());
    }

    @Test
    void bundleRuleDoesNotTouchUnrelatedProductLines() {
        long bundleId = 42L;
        CartLine bl = bundleLine(0, bundleId, 1, "200.00");
        CartLine pl = line(1, 99L, 1, "100.00"); // unrelated product

        CartContext ctx = context(List.of(bl, pl), null, Set.of());

        PromotionRule bundleRule = percentageRule("10", null, "LINE", true, 50);
        ProductBundle bundle = new ProductBundle();
        bundle.setId(bundleId);
        bundleRule.setTargetBundles(new HashSet<>(Set.of(bundle)));

        PricingResult result = engine.compute(ctx, List.of(bundleRule), null);

        // Only the bundle line is discounted (10% of 200 = 20)
        assertEquals(bd("20.00"), result.promotionSavings());
        // Product line is untouched
        LineBreakdown productBreakdown = result.lines().stream()
                .filter(l -> Long.valueOf(99L).equals(l.productId()))
                .findFirst().orElseThrow();
        assertEquals(bd("0.00"), productBreakdown.savings());
    }

}
