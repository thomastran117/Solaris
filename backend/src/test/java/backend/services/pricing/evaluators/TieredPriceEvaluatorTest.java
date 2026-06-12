package backend.services.pricing.evaluators;

import backend.models.core.PromotionRule;
import backend.services.pricing.CartLine;
import backend.services.pricing.WorkingLine;
import backend.services.pricing.config.TieredPriceConfig;
import backend.services.pricing.config.TieredPriceConfig.Breakpoint;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TieredPriceEvaluatorTest {

    private static final UUID COMPANY_ID = TestIds.uuid(99);
    private static final UUID RULE_ID    = TestIds.uuid(1);
    private static final UUID PRODUCT_A  = TestIds.uuid(10);

    private TieredPriceEvaluator evaluator;
    private PromotionRule rule;

    @BeforeEach
    void setUp() {
        evaluator = new TieredPriceEvaluator();
        rule = new PromotionRule();
        rule.setId(RULE_ID);
    }

    private WorkingLine line(int qty, String price) {
        return new WorkingLine(new CartLine(0, PRODUCT_A, null, qty, new BigDecimal(price), COMPANY_ID, null));
    }

    private TieredPriceConfig tiers(Breakpoint... bps) {
        return new TieredPriceConfig(List.of(bps));
    }

    // ── type() ───────────────────────────────────────────────────────────────

    @Test
    void type_returnsTieredPrice() {
        assertEquals(backend.models.enums.PromotionRuleType.TIERED_PRICE, evaluator.type());
    }

    // ── No matching tier ──────────────────────────────────────────────────────

    @Test
    void qtyBelowLowestBreakpoint_noSavings() {
        // breakpoint starts at minQty=5, but line has qty=3
        WorkingLine l = line(3, "10.00");
        BigDecimal savings = evaluator.apply(rule, tiers(new Breakpoint(5, new BigDecimal("8.00"))), List.of(l));
        assertEquals(new BigDecimal("0.00"), savings);
    }

    @Test
    void tierPriceNotLowerThanBase_noSavings() {
        WorkingLine l = line(5, "10.00");
        // tier unit price equals base price
        BigDecimal savings = evaluator.apply(rule, tiers(new Breakpoint(1, new BigDecimal("10.00"))), List.of(l));
        assertEquals(new BigDecimal("0.00"), savings);
    }

    @Test
    void tierPriceHigherThanBase_noSavings() {
        WorkingLine l = line(5, "10.00");
        BigDecimal savings = evaluator.apply(rule, tiers(new Breakpoint(1, new BigDecimal("12.00"))), List.of(l));
        assertEquals(new BigDecimal("0.00"), savings);
    }

    // ── Savings applied ───────────────────────────────────────────────────────

    @Test
    void singleBreakpoint_savingsEqualsQtyTimesDiscount() {
        // qty=5, base=10, tier=8 → saving = 2*5 = 10
        WorkingLine l = line(5, "10.00");
        BigDecimal savings = evaluator.apply(rule, tiers(new Breakpoint(1, new BigDecimal("8.00"))), List.of(l));
        assertEquals(new BigDecimal("10.00"), savings);
    }

    @Test
    void highestBreakpointSelected() {
        // qty=10 → should pick the 10-qty tier (6.00) not the 5-qty (8.00)
        WorkingLine l = line(10, "10.00");
        TieredPriceConfig cfg = tiers(
                new Breakpoint(5, new BigDecimal("8.00")),
                new Breakpoint(10, new BigDecimal("6.00")));
        BigDecimal savings = evaluator.apply(rule, cfg, List.of(l));
        // saving = (10-6)*10 = 40
        assertEquals(new BigDecimal("40.00"), savings);
    }

    @Test
    void multipleLines_eachPicksOwnTier() {
        WorkingLine l1 = line(3, "10.00");  // below tier
        WorkingLine l2 = line(5, "10.00");  // hits tier at minQty=5
        TieredPriceConfig cfg = tiers(new Breakpoint(5, new BigDecimal("8.00")));
        BigDecimal savings = evaluator.apply(rule, cfg, List.of(l1, l2));
        // l1 no savings; l2 saves (10-8)*5=10
        assertEquals(new BigDecimal("10.00"), savings);
    }

    // ── Empty / drained lines ─────────────────────────────────────────────────

    @Test
    void emptyLines_returnsZero() {
        BigDecimal savings = evaluator.apply(rule, tiers(new Breakpoint(1, new BigDecimal("5.00"))), List.of());
        assertEquals(new BigDecimal("0.00"), savings);
    }

    @Test
    void drainedLine_skipped() {
        WorkingLine l = line(5, "10.00");
        l.applySavings(RULE_ID, new BigDecimal("50.00")); // drain to 0
        BigDecimal savings = evaluator.apply(rule, tiers(new Breakpoint(1, new BigDecimal("8.00"))), List.of(l));
        assertEquals(new BigDecimal("0.00"), savings);
    }
}
