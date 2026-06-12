package backend.services.pricing.evaluators;

import backend.models.core.PromotionRule;
import backend.services.pricing.CartLine;
import backend.services.pricing.WorkingLine;
import backend.services.pricing.config.BogoConfig;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BogoEvaluatorTest {

    private static final UUID COMPANY_ID  = TestIds.uuid(99);
    private static final UUID RULE_ID     = TestIds.uuid(1);
    private static final UUID PRODUCT_A   = TestIds.uuid(10);
    private static final UUID PRODUCT_B   = TestIds.uuid(11);

    private BogoEvaluator evaluator;
    private PromotionRule rule;

    @BeforeEach
    void setUp() {
        evaluator = new BogoEvaluator();
        rule = new PromotionRule();
        rule.setId(RULE_ID);
    }

    private WorkingLine line(UUID productId, int qty, String price) {
        return new WorkingLine(new CartLine(0, productId, null, qty, new BigDecimal(price), COMPANY_ID, null));
    }

    private BogoConfig bogo(int triggerQty, int rewardQty, String rewardPct, int maxApplications) {
        return new BogoConfig(null, triggerQty, null, rewardQty, new BigDecimal(rewardPct), maxApplications);
    }

    // ── type() ───────────────────────────────────────────────────────────────

    @Test
    void type_returnsBogo() {
        assertEquals(backend.models.enums.PromotionRuleType.BOGO, evaluator.type());
    }

    // ── No applications ───────────────────────────────────────────────────────

    @Test
    void notEnoughTrigger_returnsZero() {
        WorkingLine l = line(PRODUCT_A, 1, "10.00"); // need 2 to trigger buy-2-get-1
        BigDecimal savings = evaluator.apply(rule, bogo(2, 1, "100", 1), List.of(l));
        assertEquals(0, savings.compareTo(BigDecimal.ZERO));
    }

    @Test
    void emptyLines_returnsZero() {
        BigDecimal savings = evaluator.apply(rule, bogo(1, 1, "100", 1), List.of());
        assertEquals(0, savings.compareTo(BigDecimal.ZERO));
    }

    // ── Buy-X-Get-Y free (100%) ───────────────────────────────────────────────

    @Test
    void buy1Get1Free_qty2_savingsEqualsUnitPrice() {
        WorkingLine l = line(PRODUCT_A, 2, "20.00");
        BigDecimal savings = evaluator.apply(rule, bogo(1, 1, "100", 1), List.of(l));
        assertEquals(new BigDecimal("20.00"), savings);
    }

    @Test
    void buy2Get1Free_qty3_savingsEqualsOneUnit() {
        WorkingLine l = line(PRODUCT_A, 3, "10.00");
        BigDecimal savings = evaluator.apply(rule, bogo(2, 1, "100", 1), List.of(l));
        assertEquals(new BigDecimal("10.00"), savings);
    }

    @Test
    void maxApplicationsCap_limits() {
        WorkingLine l = line(PRODUCT_A, 6, "10.00"); // could fire 3×buy1get1
        BigDecimal savings = evaluator.apply(rule, bogo(1, 1, "100", 2), List.of(l));
        // max 2 applications → 2 free units × $10
        assertEquals(new BigDecimal("20.00"), savings);
    }

    // ── Partial discount (50%) ────────────────────────────────────────────────

    @Test
    void buy1Get1Half_savingsIsHalfUnitPrice() {
        WorkingLine l = line(PRODUCT_A, 2, "30.00");
        BigDecimal savings = evaluator.apply(rule, bogo(1, 1, "50", 1), List.of(l));
        assertEquals(new BigDecimal("15.00"), savings);
    }

    // ── Different trigger and reward sets ─────────────────────────────────────

    @Test
    void separateTriggerAndRewardProducts_appliesCorrectly() {
        WorkingLine trigger = line(PRODUCT_A, 2, "10.00");
        WorkingLine reward  = line(PRODUCT_B, 1, "20.00");
        BogoConfig cfg = new BogoConfig(
                List.of(PRODUCT_A), 2,
                List.of(PRODUCT_B), 1,
                new BigDecimal("100"), 1);
        BigDecimal savings = evaluator.apply(rule, cfg, List.of(trigger, reward));
        assertEquals(new BigDecimal("20.00"), savings);
    }

    // ── Reward capped at line remaining ───────────────────────────────────────

    @Test
    void rewardLineAlreadyDrained_savingsFromNextLine() {
        WorkingLine trigger = line(PRODUCT_A, 1, "10.00");
        WorkingLine reward  = line(PRODUCT_A, 1, "10.00");
        // drain the reward line first
        reward.applySavings(RULE_ID, new BigDecimal("10.00"));
        // still expect zero savings (reward line has no remaining)
        BigDecimal savings = evaluator.apply(rule, bogo(1, 1, "100", 1), List.of(trigger, reward));
        assertEquals(new BigDecimal("0.00"), savings);
    }

    // ── Null triggerProductIds = all products ─────────────────────────────────

    @Test
    void nullTriggerProductIds_anyProductQualifies() {
        WorkingLine l = line(PRODUCT_B, 2, "15.00");
        // triggerProductIds = null means any product
        BigDecimal savings = evaluator.apply(rule, bogo(1, 1, "100", 1), List.of(l));
        assertEquals(new BigDecimal("15.00"), savings);
    }
}
