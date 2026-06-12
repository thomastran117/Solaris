package backend.services.risk.evaluators;

import backend.configurations.environment.RiskProperties;
import backend.http.DeviceType;
import backend.models.enums.RiskAction;
import backend.models.enums.RiskAssessmentKind;
import backend.models.enums.RiskDecision;
import backend.models.enums.RiskSignalType;
import backend.repositories.CouponRedemptionRepository;
import backend.repositories.FailedPaymentAttemptRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ReturnRepository;
import backend.repositories.UserDeviceRepository;
import backend.services.risk.RiskContext;
import backend.services.risk.RiskSignal;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for each RiskRuleEvaluator. Evaluators are pure business-logic classes
 * with mockable repository dependencies — no Spring context needed.
 */
class RiskEvaluatorsTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final Instant NOW   = Instant.parse("2026-06-02T12:00:00Z");

    /** Builds a minimal RiskContext for checkout with safe defaults. */
    private RiskContext checkoutCtx() {
        return new RiskContext(
                USER_ID, "user@example.com",
                NOW.minus(90, ChronoUnit.DAYS), // created 90 days ago
                null, Set.of(), null,
                new BigDecimal("100.00"), null, "USD",
                null, null, List.of(),
                null, "1.2.3.4", "fp-abc123",
                "Mozilla/5.0", DeviceType.DESKTOP,
                RiskAssessmentKind.CHECKOUT, NOW);
    }

    /** Builds a return-mode RiskContext with delivery timestamp. */
    private RiskContext returnCtx(Instant deliveredAt, BigDecimal total) {
        return new RiskContext(
                USER_ID, "user@example.com",
                NOW.minus(90, ChronoUnit.DAYS), null, Set.of(),
                TestIds.uuid(10), total, deliveredAt, "USD",
                null, null, List.of(),
                null, "1.2.3.4", null, null, null,
                RiskAssessmentKind.RETURN, NOW);
    }

    // ─── ReturnPatternEvaluator ───────────────────────────────────────────────

    @Nested
    class ReturnPatternEvaluatorTests {

        private ReturnRepository returnRepository;
        private ReturnPatternEvaluator evaluator;

        @BeforeEach
        void setUp() {
            returnRepository = mock(ReturnRepository.class);
            RiskProperties props = new RiskProperties();
            evaluator = new ReturnPatternEvaluator(returnRepository, props);
        }

        @Test
        void checkout_noReturns_returnsLow() {
            when(returnRepository.countByUserId(USER_ID)).thenReturn(0L);
            RiskSignal signal = evaluator.evaluate(checkoutCtx());
            assertEquals(RiskDecision.LOW, signal.decision());
        }

        @Test
        void checkout_fewReturns_belowDenominator_returnsLow() {
            when(returnRepository.countByUserId(USER_ID)).thenReturn(2L); // < rateMinDenominator(5)
            RiskSignal signal = evaluator.evaluate(checkoutCtx());
            assertEquals(RiskDecision.LOW, signal.decision());
        }

        @Test
        void checkout_highLifetimeReturns_returnsMedium() {
            // >= rateMinDenominator(5) * 2 = 10 → MEDIUM
            when(returnRepository.countByUserId(USER_ID)).thenReturn(12L);
            when(returnRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                    .thenReturn(1L); // recent < repeatCountHigh(3)
            RiskSignal signal = evaluator.evaluate(checkoutCtx());
            assertEquals(RiskDecision.MEDIUM, signal.decision());
        }

        @Test
        void checkout_manyRecentReturns_returnsHigh() {
            when(returnRepository.countByUserId(USER_ID)).thenReturn(8L);
            // recent >= repeatCountHigh(3) → HIGH
            when(returnRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                    .thenReturn(5L);
            RiskSignal signal = evaluator.evaluate(checkoutCtx());
            assertEquals(RiskDecision.HIGH, signal.decision());
        }

        @Test
        void returnMode_noRecentReturns_lowValueOrder_returnsLow() {
            when(returnRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                    .thenReturn(0L);
            // Small order, delivered days ago
            RiskSignal signal = evaluator.evaluate(
                    returnCtx(NOW.minus(5, ChronoUnit.DAYS), new BigDecimal("20.00")));
            assertEquals(RiskDecision.LOW, signal.decision());
        }

        @Test
        void returnMode_fastReturnHighValue_returnsHigh() {
            when(returnRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                    .thenReturn(0L);
            // High value ($600) delivered 30 minutes ago → fast return
            Instant deliveredAt = NOW.minus(30, ChronoUnit.MINUTES);
            RiskSignal signal = evaluator.evaluate(
                    returnCtx(deliveredAt, new BigDecimal("600.00")));
            assertEquals(RiskDecision.HIGH, signal.decision());
        }

        @Test
        void returnMode_manyRecentReturns_returnsHigh() {
            // recent >= repeatCountHigh(3)
            when(returnRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                    .thenReturn(4L);
            RiskSignal signal = evaluator.evaluate(
                    returnCtx(NOW.minus(10, ChronoUnit.DAYS), new BigDecimal("50.00")));
            assertEquals(RiskDecision.HIGH, signal.decision());
        }
    }

    // ─── FailedPaymentVelocityEvaluator ──────────────────────────────────────

    @Nested
    class FailedPaymentVelocityEvaluatorTests {

        private FailedPaymentAttemptRepository failedRepo;
        private FailedPaymentVelocityEvaluator evaluator;

        @BeforeEach
        void setUp() {
            failedRepo = mock(FailedPaymentAttemptRepository.class);
            RiskProperties props = new RiskProperties();
            evaluator = new FailedPaymentVelocityEvaluator(failedRepo, props);
        }

        @Test
        void noFailures_returnsLow() {
            when(failedRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(0L);
            RiskSignal signal = evaluator.evaluate(checkoutCtx());
            assertEquals(RiskDecision.LOW, signal.decision());
        }

        @Test
        void userMediumFails_returnsMedium() {
            // mediumCount default = 2
            when(failedRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(3L);
            when(failedRepo.countByIpAndCreatedAtAfter(any(), any())).thenReturn(0L);
            RiskSignal signal = evaluator.evaluate(checkoutCtx());
            assertEquals(RiskDecision.MEDIUM, signal.decision());
        }

        @Test
        void userHighFails_returnsHigh() {
            // highCount default = 5
            when(failedRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(6L);
            when(failedRepo.countByIpAndCreatedAtAfter(any(), any())).thenReturn(0L);
            RiskSignal signal = evaluator.evaluate(checkoutCtx());
            assertEquals(RiskDecision.HIGH, signal.decision());
        }

        @Test
        void ipHighFails_returnsHigh() {
            // ipHighCount default = 10
            when(failedRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(0L);
            when(failedRepo.countByIpAndCreatedAtAfter(any(), any())).thenReturn(12L);
            RiskSignal signal = evaluator.evaluate(checkoutCtx());
            assertEquals(RiskDecision.HIGH, signal.decision());
        }

        @Test
        void noClientIp_skipsIpCheck() {
            RiskContext ctx = new RiskContext(
                    USER_ID, "u@e.com", NOW.minus(30, ChronoUnit.DAYS), null, Set.of(),
                    null, BigDecimal.TEN, null, "USD",
                    null, null, List.of(),
                    null, null, null, null, null, // no IP
                    RiskAssessmentKind.CHECKOUT, NOW);
            when(failedRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(0L);
            RiskSignal signal = evaluator.evaluate(ctx);
            assertEquals(RiskDecision.LOW, signal.decision());
        }
    }

    // ─── MultiAccountDeviceVelocityEvaluator ─────────────────────────────────

    @Nested
    class MultiAccountDeviceVelocityEvaluatorTests {

        private UserDeviceRepository deviceRepo;
        private MultiAccountDeviceVelocityEvaluator evaluator;

        @BeforeEach
        void setUp() {
            deviceRepo = mock(UserDeviceRepository.class);
            RiskProperties props = new RiskProperties();
            evaluator = new MultiAccountDeviceVelocityEvaluator(deviceRepo, props);
        }

        @Test
        void noDeviceFingerprint_returnsNeutral() {
            RiskContext ctx = new RiskContext(
                    USER_ID, "u@e.com", NOW.minus(30, ChronoUnit.DAYS), null, Set.of(),
                    null, BigDecimal.TEN, null, "USD",
                    null, null, List.of(),
                    null, "1.2.3.4", null, null, null, // null fingerprint
                    RiskAssessmentKind.CHECKOUT, NOW);
            RiskSignal signal = evaluator.evaluate(ctx);
            assertEquals(RiskDecision.NEUTRAL, signal.decision());
        }

        @Test
        void singleAccount_returnsLow() {
            when(deviceRepo.countDistinctUserIdByFingerprint("fp-abc123")).thenReturn(1L);
            when(deviceRepo.countByFingerprintAndCreatedAtAfter(eq("fp-abc123"), any())).thenReturn(1L);
            RiskSignal signal = evaluator.evaluate(checkoutCtx());
            assertEquals(RiskDecision.LOW, signal.decision());
        }

        @Test
        void mediumDistinctUsers_returnsMedium() {
            // distinctUsersMedium default = 2
            when(deviceRepo.countDistinctUserIdByFingerprint("fp-abc123")).thenReturn(2L);
            when(deviceRepo.countByFingerprintAndCreatedAtAfter(eq("fp-abc123"), any())).thenReturn(1L);
            RiskSignal signal = evaluator.evaluate(checkoutCtx());
            assertEquals(RiskDecision.MEDIUM, signal.decision());
        }

        @Test
        void highDistinctUsers_returnsHigh() {
            // distinctUsersHigh default = 4
            when(deviceRepo.countDistinctUserIdByFingerprint("fp-abc123")).thenReturn(5L);
            when(deviceRepo.countByFingerprintAndCreatedAtAfter(eq("fp-abc123"), any())).thenReturn(1L);
            RiskSignal signal = evaluator.evaluate(checkoutCtx());
            assertEquals(RiskDecision.HIGH, signal.decision());
        }
    }

    // ─── CouponAbuseEvaluator ─────────────────────────────────────────────────

    @Nested
    class CouponAbuseEvaluatorTests {

        private CouponRedemptionRepository redemptionRepo;
        private OrderRepository orderRepository;
        private CouponAbuseEvaluator evaluator;

        @BeforeEach
        void setUp() {
            redemptionRepo = mock(CouponRedemptionRepository.class);
            orderRepository = mock(OrderRepository.class);
            RiskProperties props = new RiskProperties();
            evaluator = new CouponAbuseEvaluator(redemptionRepo, orderRepository, props);
        }

        @Test
        void noCouponApplied_returnsNeutral() {
            // couponCode = null in default checkoutCtx()
            RiskSignal signal = evaluator.evaluate(checkoutCtx());
            assertEquals(RiskDecision.NEUTRAL, signal.decision());
        }

        @Test
        void highUserRedemptions_returnsHigh() {
            // perUser24hHigh default = 5
            RiskContext ctx = ctxWithCoupon("SAVE10", new BigDecimal("5.00"), new BigDecimal("100.00"));
            when(redemptionRepo.countByUser_IdAndRedeemedAtAfter(eq(USER_ID), any())).thenReturn(6L);
            when(redemptionRepo.countByIpAndRedeemedAtAfter(any(), any())).thenReturn(0L);
            RiskSignal signal = evaluator.evaluate(ctx);
            assertEquals(RiskDecision.HIGH, signal.decision());
        }

        @Test
        void firstOrderHighDiscount_returnsMedium() {
            // firstOrderPctThreshold default = 20%
            // discount = 25% of total → MEDIUM
            RiskContext ctx = ctxWithCoupon("NEW25", new BigDecimal("25.00"), new BigDecimal("100.00"));
            when(redemptionRepo.countByUser_IdAndRedeemedAtAfter(eq(USER_ID), any())).thenReturn(0L);
            when(redemptionRepo.countByIpAndRedeemedAtAfter(any(), any())).thenReturn(0L);
            when(orderRepository.countByUserIdExcludingStatus(eq(USER_ID), any())).thenReturn(0L); // first order
            RiskSignal signal = evaluator.evaluate(ctx);
            assertEquals(RiskDecision.MEDIUM, signal.decision());
        }

        @Test
        void returningUser_lowDiscount_returnsLow() {
            RiskContext ctx = ctxWithCoupon("5OFF", new BigDecimal("5.00"), new BigDecimal("100.00"));
            when(redemptionRepo.countByUser_IdAndRedeemedAtAfter(eq(USER_ID), any())).thenReturn(0L);
            when(redemptionRepo.countByIpAndRedeemedAtAfter(any(), any())).thenReturn(0L);
            when(orderRepository.countByUserIdExcludingStatus(eq(USER_ID), any())).thenReturn(3L); // not first order
            RiskSignal signal = evaluator.evaluate(ctx);
            assertEquals(RiskDecision.LOW, signal.decision());
        }

        private RiskContext ctxWithCoupon(String code, BigDecimal discount, BigDecimal total) {
            return new RiskContext(
                    USER_ID, "u@e.com",
                    NOW.minus(90, ChronoUnit.DAYS), null, Set.of(),
                    null, total, null, "USD",
                    code, discount, List.of(),
                    null, "1.2.3.4", null, null, null,
                    RiskAssessmentKind.CHECKOUT, NOW);
        }
    }

    // ─── ShippingIpCountryMismatchEvaluator ───────────────────────────────────

    @Nested
    class ShippingIpCountryMismatchEvaluatorTests {

        @Test
        void alwaysReturnsNeutral_stubImplementation() {
            ShippingIpCountryMismatchEvaluator evaluator = new ShippingIpCountryMismatchEvaluator();
            RiskSignal signal = evaluator.evaluate(checkoutCtx());
            assertEquals(RiskDecision.NEUTRAL, signal.decision());
        }

        @Test
        void type_returnsCorrectSignalType() {
            ShippingIpCountryMismatchEvaluator evaluator = new ShippingIpCountryMismatchEvaluator();
            assertEquals(RiskSignalType.SHIPPING_IP_COUNTRY_MISMATCH, evaluator.type());
        }
    }
}
