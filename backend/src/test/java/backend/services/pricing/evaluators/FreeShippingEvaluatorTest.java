package backend.services.pricing.evaluators;

import backend.models.core.PromotionRule;
import backend.services.pricing.CartLine;
import backend.services.pricing.WorkingLine;
import backend.services.pricing.config.FreeShippingConfig;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FreeShippingEvaluatorTest {

    private static final UUID COMPANY_ID = TestIds.uuid(99);

    private FreeShippingEvaluator evaluator;
    private PromotionRule rule;

    @BeforeEach
    void setUp() {
        evaluator = new FreeShippingEvaluator();
        rule = new PromotionRule();
        rule.setId(TestIds.uuid(1));
    }

    private WorkingLine line(int qty, String price) {
        return new WorkingLine(new CartLine(0, TestIds.uuid(10), null, qty, new BigDecimal(price), COMPANY_ID, null));
    }

    @Test
    void type_returnsFreeShipping() {
        assertEquals(backend.models.enums.PromotionRuleType.FREE_SHIPPING, evaluator.type());
    }

    @Test
    void apply_emptyLines_returnsZero() {
        assertEquals(BigDecimal.ZERO, evaluator.apply(rule, new FreeShippingConfig(null, false), List.of()));
    }

    @Test
    void apply_withLines_alwaysReturnsZero() {
        WorkingLine l = line(2, "50.00");
        BigDecimal savings = evaluator.apply(rule, new FreeShippingConfig(new BigDecimal("10.00"), false), List.of(l));
        assertEquals(BigDecimal.ZERO, savings);
    }

    @Test
    void apply_doesNotMutateLineRemaining() {
        WorkingLine l = line(1, "30.00");
        evaluator.apply(rule, new FreeShippingConfig(null, false), List.of(l));
        assertEquals(new BigDecimal("30.00"), l.remaining());
    }
}
