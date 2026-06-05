package backend.services.pricing.evaluators;

import backend.models.core.PromotionRule;
import backend.services.pricing.CartLine;
import backend.services.pricing.WorkingLine;
import backend.services.pricing.config.FixedOffConfig;
import backend.services.pricing.config.PromotionScope;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FixedOffEvaluatorTest {

    private static final UUID COMPANY_ID = TestIds.uuid(99);
    private static final UUID RULE_ID    = TestIds.uuid(1);

    private FixedOffEvaluator evaluator;
    private PromotionRule rule;

    @BeforeEach
    void setUp() {
        evaluator = new FixedOffEvaluator();
        rule = new PromotionRule();
        rule.setId(RULE_ID);
    }

    private WorkingLine line(int qty, String price) {
        return new WorkingLine(new CartLine(0, TestIds.uuid(10), null, qty, new BigDecimal(price), COMPANY_ID, null));
    }

    // ── type() ───────────────────────────────────────────────────────────────

    @Test
    void type_returnsFixedOff() {
        assertEquals(backend.models.enums.PromotionRuleType.FIXED_OFF, evaluator.type());
    }

    // ── LINE scope ────────────────────────────────────────────────────────────

    @Test
    void lineScope_deductsFixedAmountPerLine() {
        WorkingLine l1 = line(1, "50.00");
        WorkingLine l2 = line(1, "30.00");
        BigDecimal savings = evaluator.apply(rule,
                new FixedOffConfig(new BigDecimal("10.00"), PromotionScope.LINE),
                List.of(l1, l2));
        assertEquals(new BigDecimal("20.00"), savings);
        assertEquals(new BigDecimal("40.00"), l1.remaining());
        assertEquals(new BigDecimal("20.00"), l2.remaining());
    }

    @Test
    void lineScope_cappedAtLineRemaining() {
        WorkingLine l = line(1, "5.00");
        BigDecimal savings = evaluator.apply(rule,
                new FixedOffConfig(new BigDecimal("20.00"), PromotionScope.LINE),
                List.of(l));
        assertEquals(new BigDecimal("5.00"), savings);
    }

    @Test
    void lineScope_emptyLines_returnsZero() {
        BigDecimal savings = evaluator.apply(rule,
                new FixedOffConfig(new BigDecimal("10.00"), PromotionScope.LINE),
                List.of());
        assertEquals(new BigDecimal("0.00"), savings);
    }

    // ── ORDER scope ───────────────────────────────────────────────────────────

    @Test
    void orderScope_deductsOnceDistributedAcrossLines() {
        WorkingLine l1 = line(1, "60.00");
        WorkingLine l2 = line(1, "40.00");
        // pool = 100, discount 10, l1 gets 6.00, l2 gets 4.00
        BigDecimal savings = evaluator.apply(rule,
                new FixedOffConfig(new BigDecimal("10.00"), PromotionScope.ORDER),
                List.of(l1, l2));
        assertEquals(new BigDecimal("10.00"), savings);
        assertEquals(new BigDecimal("54.00"), l1.remaining());
        assertEquals(new BigDecimal("36.00"), l2.remaining());
    }

    @Test
    void orderScope_discountLargerThanPool_cappedAtPool() {
        WorkingLine l = line(1, "5.00");
        BigDecimal savings = evaluator.apply(rule,
                new FixedOffConfig(new BigDecimal("100.00"), PromotionScope.ORDER),
                List.of(l));
        assertEquals(new BigDecimal("5.00"), savings);
    }

    @Test
    void orderScope_zeroPool_returnsZero() {
        WorkingLine l = line(1, "10.00");
        l.applySavings(RULE_ID, new BigDecimal("10.00")); // drain
        BigDecimal savings = evaluator.apply(rule,
                new FixedOffConfig(new BigDecimal("5.00"), PromotionScope.ORDER),
                List.of(l));
        assertEquals(BigDecimal.ZERO, savings);
    }
}
