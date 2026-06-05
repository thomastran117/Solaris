package backend.services.pricing;

import backend.testutil.TestIds;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkingLineTest {

    private static final UUID PRODUCT_ID = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final UUID RULE_ID = TestIds.uuid(3);

    private static CartLine cartLine(int qty, String price) {
        return new CartLine(0, PRODUCT_ID, null, qty, new BigDecimal(price), COMPANY_ID, null);
    }

    private static WorkingLine line(int qty, String price) {
        return new WorkingLine(cartLine(qty, price));
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    void remaining_startEqualsQtyTimesUnitPrice() {
        WorkingLine l = line(3, "10.00");
        assertEquals(new BigDecimal("30.00"), l.remaining());
    }

    @Test
    void savings_startAtZero() {
        assertEquals(BigDecimal.ZERO.setScale(2), line(1, "10.00").savings());
    }

    // ── applySavings ──────────────────────────────────────────────────────────

    @Test
    void applySavings_normalAmount_deductsFromRemaining() {
        WorkingLine l = line(1, "10.00");
        BigDecimal taken = l.applySavings(RULE_ID, new BigDecimal("3.00"));
        assertEquals(new BigDecimal("3.00"), taken);
        assertEquals(new BigDecimal("7.00"), l.remaining());
        assertEquals(new BigDecimal("3.00"), l.savings());
    }

    @Test
    void applySavings_cappedAtRemaining() {
        WorkingLine l = line(1, "10.00");
        BigDecimal taken = l.applySavings(RULE_ID, new BigDecimal("15.00"));
        assertEquals(new BigDecimal("10.00"), taken);
        assertEquals(BigDecimal.ZERO.setScale(2), l.remaining());
    }

    @Test
    void applySavings_zeroAmount_noOp() {
        WorkingLine l = line(1, "10.00");
        BigDecimal taken = l.applySavings(RULE_ID, BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO, taken);
        assertEquals(new BigDecimal("10.00"), l.remaining());
    }

    @Test
    void applySavings_nullAmount_noOp() {
        WorkingLine l = line(1, "10.00");
        BigDecimal taken = l.applySavings(RULE_ID, null);
        assertEquals(BigDecimal.ZERO, taken);
        assertEquals(new BigDecimal("10.00"), l.remaining());
    }

    @Test
    void applySavings_negativeAmount_noOp() {
        WorkingLine l = line(1, "10.00");
        BigDecimal taken = l.applySavings(RULE_ID, new BigDecimal("-5.00"));
        assertEquals(BigDecimal.ZERO, taken);
        assertEquals(new BigDecimal("10.00"), l.remaining());
    }

    @Test
    void applySavings_whenRemainingZero_returnsZero() {
        WorkingLine l = line(1, "10.00");
        l.applySavings(RULE_ID, new BigDecimal("10.00")); // drain
        BigDecimal taken = l.applySavings(RULE_ID, new BigDecimal("5.00"));
        assertEquals(0, taken.compareTo(BigDecimal.ZERO));
    }

    @Test
    void applySavings_nullRuleId_savingsStillApplied() {
        WorkingLine l = line(1, "10.00");
        l.applySavings(null, new BigDecimal("5.00"));
        assertEquals(new BigDecimal("5.00"), l.savings());
    }

    // ── BOGO counters ─────────────────────────────────────────────────────────

    @Test
    void bogoTriggerAvailable_initialEqualsQuantity() {
        WorkingLine l = line(5, "1.00");
        assertEquals(5, l.bogoTriggerAvailable());
    }

    @Test
    void consumeBogoTrigger_reducesAvailability() {
        WorkingLine l = line(5, "1.00");
        l.consumeBogoTrigger(2);
        assertEquals(3, l.bogoTriggerAvailable());
        assertEquals(3, l.bogoRewardAvailable());
    }

    @Test
    void consumeBogoReward_reducesAvailability() {
        WorkingLine l = line(5, "1.00");
        l.consumeBogoReward(1);
        assertEquals(4, l.bogoRewardAvailable());
        assertEquals(4, l.bogoTriggerAvailable());
    }

    @Test
    void bogoTriggerAndRewardConsumed_reduceBothAvailable() {
        WorkingLine l = line(4, "1.00");
        l.consumeBogoTrigger(1);
        l.consumeBogoReward(1);
        // trigger+reward consumed = 2, so available = 4 - 2 = 2
        assertEquals(2, l.bogoTriggerAvailable());
        assertEquals(2, l.bogoRewardAvailable());
    }

    // ── toBreakdown ───────────────────────────────────────────────────────────

    @Test
    void toBreakdown_reflectsState() {
        WorkingLine l = line(2, "10.00");
        l.applySavings(RULE_ID, new BigDecimal("5.00"));
        LineBreakdown bd = l.toBreakdown();
        assertEquals(PRODUCT_ID, bd.productId());
        assertEquals(2, bd.quantity());
        assertEquals(new BigDecimal("10.00"), bd.unitBasePrice());
        assertEquals(new BigDecimal("5.00"), bd.savings());
        assertEquals(new BigDecimal("15.00"), bd.effectiveLineTotal());
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    @Test
    void accessors_delegateToSource() {
        WorkingLine l = line(3, "7.50");
        assertEquals(PRODUCT_ID, l.productId());
        assertNull(l.variantId());
        assertEquals(3, l.quantity());
        assertEquals(new BigDecimal("7.50"), l.unitBasePrice());
        assertEquals(COMPANY_ID, l.companyId());
        assertNull(l.bundleId());
    }
}
