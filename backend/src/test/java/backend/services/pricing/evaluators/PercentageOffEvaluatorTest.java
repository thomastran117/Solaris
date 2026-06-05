package backend.services.pricing.evaluators;

import backend.models.core.PromotionRule;
import backend.services.pricing.CartLine;
import backend.services.pricing.WorkingLine;
import backend.services.pricing.config.PercentageOffConfig;
import backend.services.pricing.config.PromotionScope;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PercentageOffEvaluatorTest {

    private static final UUID COMPANY_ID = TestIds.uuid(99);
    private static final UUID RULE_ID    = TestIds.uuid(1);

    private PercentageOffEvaluator evaluator;
    private PromotionRule rule;

    @BeforeEach
    void setUp() {
        evaluator = new PercentageOffEvaluator();
        rule = new PromotionRule();
        rule.setId(RULE_ID);
    }

    private WorkingLine line(int qty, String price) {
        return new WorkingLine(new CartLine(0, TestIds.uuid(10), null, qty, new BigDecimal(price), COMPANY_ID, null));
    }

    private PercentageOffConfig cfg(String percent, PromotionScope scope) {
        return new PercentageOffConfig(new BigDecimal(percent), null, scope);
    }

    private PercentageOffConfig cfgCapped(String percent, String cap, PromotionScope scope) {
        return new PercentageOffConfig(new BigDecimal(percent), new BigDecimal(cap), scope);
    }

    // ── type() ───────────────────────────────────────────────────────────────

    @Test
    void type_returnsPercentageOff() {
        assertEquals(backend.models.enums.PromotionRuleType.PERCENTAGE_OFF, evaluator.type());
    }

    // ── LINE scope ────────────────────────────────────────────────────────────

    @Test
    void lineScope_10percent_returnsCorrectSavings() {
        WorkingLine l = line(1, "100.00");
        BigDecimal savings = evaluator.apply(rule, cfg("10", PromotionScope.LINE), List.of(l));
        assertEquals(new BigDecimal("10.00"), savings);
        assertEquals(new BigDecimal("90.00"), l.remaining());
    }

    @Test
    void lineScope_multipleLines_savingsAppliedToEach() {
        WorkingLine l1 = line(1, "100.00");
        WorkingLine l2 = line(1, "50.00");
        BigDecimal savings = evaluator.apply(rule, cfg("10", PromotionScope.LINE), List.of(l1, l2));
        assertEquals(new BigDecimal("15.00"), savings);
    }

    @Test
    void lineScope_100percent_fullSavings() {
        WorkingLine l = line(2, "25.00");
        BigDecimal savings = evaluator.apply(rule, cfg("100", PromotionScope.LINE), List.of(l));
        assertEquals(new BigDecimal("50.00"), savings);
        assertEquals(BigDecimal.ZERO.setScale(2), l.remaining());
    }

    @Test
    void lineScope_cappedAtMaxDiscount() {
        WorkingLine l = line(1, "200.00");
        BigDecimal savings = evaluator.apply(rule, cfgCapped("50", "30.00", PromotionScope.LINE), List.of(l));
        assertEquals(new BigDecimal("30.00"), savings);
    }

    @Test
    void lineScope_emptyLines_returnsZero() {
        BigDecimal savings = evaluator.apply(rule, cfg("10", PromotionScope.LINE), List.of());
        assertEquals(new BigDecimal("0.00"), savings);
    }

    @Test
    void lineScope_zeroRemainingLine_skipped() {
        WorkingLine l = line(1, "10.00");
        l.applySavings(RULE_ID, new BigDecimal("10.00")); // drain
        BigDecimal savings = evaluator.apply(rule, cfg("10", PromotionScope.LINE), List.of(l));
        assertEquals(new BigDecimal("0.00"), savings);
    }

    // ── ORDER scope ───────────────────────────────────────────────────────────

    @Test
    void orderScope_10percent_distributedAcrossLines() {
        WorkingLine l1 = line(1, "100.00");
        WorkingLine l2 = line(1, "100.00");
        BigDecimal savings = evaluator.apply(rule, cfg("10", PromotionScope.ORDER), List.of(l1, l2));
        assertEquals(new BigDecimal("20.00"), savings);
    }

    @Test
    void orderScope_savingsDistributedProportionally() {
        WorkingLine l1 = line(1, "75.00");
        WorkingLine l2 = line(1, "25.00");
        // 10% of 100 = 10; l1 gets 7.50, l2 gets 2.50
        BigDecimal savings = evaluator.apply(rule, cfg("10", PromotionScope.ORDER), List.of(l1, l2));
        assertEquals(new BigDecimal("10.00"), savings);
        assertEquals(new BigDecimal("67.50"), l1.remaining());
        assertEquals(new BigDecimal("22.50"), l2.remaining());
    }

    @Test
    void orderScope_cappedAtMaxDiscount() {
        WorkingLine l = line(1, "200.00");
        BigDecimal savings = evaluator.apply(rule, cfgCapped("50", "30.00", PromotionScope.ORDER), List.of(l));
        assertEquals(new BigDecimal("30.00"), savings);
    }

    @Test
    void orderScope_allLinesZeroRemaining_returnsZero() {
        WorkingLine l = line(1, "10.00");
        l.applySavings(RULE_ID, new BigDecimal("10.00"));
        BigDecimal savings = evaluator.apply(rule, cfg("10", PromotionScope.ORDER), List.of(l));
        assertEquals(BigDecimal.ZERO, savings);
    }
}
