package backend.services.risk.evaluators;

import backend.configurations.environment.RiskProperties;
import backend.http.DeviceType;
import backend.models.enums.RiskAssessmentKind;
import backend.models.enums.RiskDecision;
import backend.repositories.CouponRedemptionRepository;
import backend.repositories.OrderRepository;
import backend.services.risk.RiskContext;
import backend.services.risk.RiskSignal;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CouponAbuseEvaluatorTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final String CLIENT_IP = "10.0.0.1";

    private CouponRedemptionRepository redemptionRepo;
    private OrderRepository orderRepo;
    private RiskProperties props;
    private CouponAbuseEvaluator evaluator;

    @BeforeEach
    void setUp() {
        redemptionRepo = mock(CouponRedemptionRepository.class);
        orderRepo = mock(OrderRepository.class);
        props = new RiskProperties();
        evaluator = new CouponAbuseEvaluator(redemptionRepo, orderRepo, props);
    }

    private RiskContext ctx(String couponCode, BigDecimal discount, BigDecimal total) {
        return new RiskContext(
                USER_ID, "buyer@example.com",
                Instant.now().minusSeconds(3600), null,
                Set.of(), null, total, null,
                "USD", couponCode, discount,
                List.of(), "US", CLIENT_IP,
                null, "Mozilla/5.0", DeviceType.DESKTOP,
                RiskAssessmentKind.CHECKOUT, Instant.now());
    }

    // ── No coupon ─────────────────────────────────────────────────────────────

    @Test
    void noCoupon_returnsNeutral() {
        RiskSignal signal = evaluator.evaluate(ctx(null, null, new BigDecimal("100")));
        assertEquals(RiskDecision.NEUTRAL, signal.decision());
        verifyNoInteractions(redemptionRepo, orderRepo);
    }

    @Test
    void blankCoupon_returnsNeutral() {
        RiskSignal signal = evaluator.evaluate(ctx("   ", null, new BigDecimal("100")));
        assertEquals(RiskDecision.NEUTRAL, signal.decision());
    }

    // ── Per-user high redemptions ──────────────────────────────────────────────

    @Test
    void highUserRedemptions_returnsHigh() {
        when(redemptionRepo.countByUser_IdAndRedeemedAtAfter(eq(USER_ID), any())).thenReturn(5L);
        RiskSignal signal = evaluator.evaluate(ctx("PROMO10", null, new BigDecimal("100")));
        assertEquals(RiskDecision.HIGH, signal.decision());
        assertEquals(45, signal.scoreContribution());
    }

    @Test
    void atThresholdUserRedemptions_returnsHigh() {
        // default perUser24hHigh = 5
        when(redemptionRepo.countByUser_IdAndRedeemedAtAfter(eq(USER_ID), any())).thenReturn(5L);
        assertEquals(RiskDecision.HIGH,
                evaluator.evaluate(ctx("CODE", null, new BigDecimal("50"))).decision());
    }

    // ── Per-IP high redemptions ───────────────────────────────────────────────

    @Test
    void highIpRedemptions_returnsHigh() {
        when(redemptionRepo.countByUser_IdAndRedeemedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        when(redemptionRepo.countByIpAndRedeemedAtAfter(eq(CLIENT_IP), any())).thenReturn(10L);
        RiskSignal signal = evaluator.evaluate(ctx("PROMO10", null, new BigDecimal("100")));
        assertEquals(RiskDecision.HIGH, signal.decision());
        assertEquals(35, signal.scoreContribution());
    }

    @Test
    void noClientIp_ipCheckSkipped() {
        when(redemptionRepo.countByUser_IdAndRedeemedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        when(orderRepo.countByUserIdExcludingStatus(any(), any())).thenReturn(3L);

        RiskContext ctxNoIp = new RiskContext(
                USER_ID, "buyer@example.com",
                Instant.now().minusSeconds(3600), null, Set.of(),
                null, new BigDecimal("100"), null, "USD",
                "PROMO10", new BigDecimal("10"), List.of(),
                "US", null, null, "Mozilla/5.0",
                DeviceType.DESKTOP, RiskAssessmentKind.CHECKOUT, Instant.now());

        RiskSignal signal = evaluator.evaluate(ctxNoIp);
        assertEquals(RiskDecision.LOW, signal.decision());
        verify(redemptionRepo, never()).countByIpAndRedeemedAtAfter(any(), any());
    }

    // ── First-order high discount ─────────────────────────────────────────────

    @Test
    void firstOrderHighDiscount_returnsMedium() {
        when(redemptionRepo.countByUser_IdAndRedeemedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        when(redemptionRepo.countByIpAndRedeemedAtAfter(eq(CLIENT_IP), any())).thenReturn(0L);
        when(orderRepo.countByUserIdExcludingStatus(any(), any())).thenReturn(0L);
        // discount=25, total=75 → gross=100, pct=25% ≥ threshold(20%)
        RiskSignal signal = evaluator.evaluate(ctx("PROMO10", new BigDecimal("25"), new BigDecimal("75")));
        assertEquals(RiskDecision.MEDIUM, signal.decision());
        assertEquals(25, signal.scoreContribution());
    }

    @Test
    void firstOrderLowDiscount_returnsLow() {
        when(redemptionRepo.countByUser_IdAndRedeemedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        when(redemptionRepo.countByIpAndRedeemedAtAfter(eq(CLIENT_IP), any())).thenReturn(0L);
        when(orderRepo.countByUserIdExcludingStatus(any(), any())).thenReturn(0L);
        // discount=5, total=95 → gross=100, pct=5% < threshold(20%)
        RiskSignal signal = evaluator.evaluate(ctx("SAVE5", new BigDecimal("5"), new BigDecimal("95")));
        assertEquals(RiskDecision.LOW, signal.decision());
    }

    @Test
    void notFirstOrder_noMediumSignal() {
        when(redemptionRepo.countByUser_IdAndRedeemedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        when(redemptionRepo.countByIpAndRedeemedAtAfter(eq(CLIENT_IP), any())).thenReturn(0L);
        when(orderRepo.countByUserIdExcludingStatus(any(), any())).thenReturn(5L);
        RiskSignal signal = evaluator.evaluate(ctx("PROMO10", new BigDecimal("25"), new BigDecimal("75")));
        assertEquals(RiskDecision.LOW, signal.decision());
    }

    // ── Null/zero total guards ────────────────────────────────────────────────

    @Test
    void nullDiscount_firstOrderCheckSkipped_returnsLow() {
        when(redemptionRepo.countByUser_IdAndRedeemedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        when(redemptionRepo.countByIpAndRedeemedAtAfter(eq(CLIENT_IP), any())).thenReturn(0L);
        when(orderRepo.countByUserIdExcludingStatus(any(), any())).thenReturn(0L);
        RiskSignal signal = evaluator.evaluate(ctx("CODE", null, new BigDecimal("100")));
        assertEquals(RiskDecision.LOW, signal.decision());
    }

    @Test
    void zeroTotal_firstOrderCheckSkipped_returnsLow() {
        when(redemptionRepo.countByUser_IdAndRedeemedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        when(redemptionRepo.countByIpAndRedeemedAtAfter(eq(CLIENT_IP), any())).thenReturn(0L);
        when(orderRepo.countByUserIdExcludingStatus(any(), any())).thenReturn(0L);
        RiskSignal signal = evaluator.evaluate(ctx("CODE", new BigDecimal("10"), BigDecimal.ZERO));
        assertEquals(RiskDecision.LOW, signal.decision());
    }

    // ── type() ────────────────────────────────────────────────────────────────

    @Test
    void type_returnsCouponAbuse() {
        assertEquals(backend.models.enums.RiskSignalType.COUPON_ABUSE, evaluator.type());
    }
}
