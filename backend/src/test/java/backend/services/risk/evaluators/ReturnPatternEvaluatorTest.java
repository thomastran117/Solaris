package backend.services.risk.evaluators;

import backend.configurations.environment.RiskProperties;
import backend.http.DeviceType;
import backend.models.enums.RiskAssessmentKind;
import backend.models.enums.RiskDecision;
import backend.repositories.ReturnRepository;
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

class ReturnPatternEvaluatorTest {

    private static final UUID USER_ID = TestIds.uuid(1);

    private ReturnRepository returnRepo;
    private RiskProperties props;
    private ReturnPatternEvaluator evaluator;

    @BeforeEach
    void setUp() {
        returnRepo = mock(ReturnRepository.class);
        props = new RiskProperties();
        evaluator = new ReturnPatternEvaluator(returnRepo, props);
    }

    private RiskContext checkoutCtx() {
        return new RiskContext(
                USER_ID, "buyer@example.com",
                Instant.now().minusSeconds(3600), null,
                Set.of(), null, new BigDecimal("200"), null,
                "USD", null, null, List.of(),
                "US", "10.0.0.1", null, "Mozilla/5.0",
                DeviceType.DESKTOP, RiskAssessmentKind.CHECKOUT, Instant.now());
    }

    private RiskContext returnCtx(Instant deliveredAt, BigDecimal total) {
        Instant now = Instant.now();
        return new RiskContext(
                USER_ID, "buyer@example.com",
                now.minusSeconds(86400), null,
                Set.of(), TestIds.uuid(9), total, deliveredAt,
                "USD", null, null, List.of(),
                "US", "10.0.0.1", null, "Mozilla/5.0",
                DeviceType.DESKTOP, RiskAssessmentKind.RETURN, now);
    }

    // ── type() ────────────────────────────────────────────────────────────────

    @Test
    void type_returnsReturnPattern() {
        assertEquals(backend.models.enums.RiskSignalType.RETURN_PATTERN, evaluator.type());
    }

    // ── CHECKOUT mode ─────────────────────────────────────────────────────────

    @Test
    void checkout_noReturns_returnsLow() {
        when(returnRepo.countByUserId(USER_ID)).thenReturn(0L);
        assertEquals(RiskDecision.LOW, evaluator.evaluate(checkoutCtx()).decision());
    }

    @Test
    void checkout_belowMinDenominator_returnsLow() {
        // default rateMinDenominator = 5; user has 3 returns
        when(returnRepo.countByUserId(USER_ID)).thenReturn(3L);
        assertEquals(RiskDecision.LOW, evaluator.evaluate(checkoutCtx()).decision());
    }

    @Test
    void checkout_highRecentReturns_returnsHigh() {
        // default repeatCountHigh = 3; recent = 4
        when(returnRepo.countByUserId(USER_ID)).thenReturn(10L);
        when(returnRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(4L);
        RiskSignal signal = evaluator.evaluate(checkoutCtx());
        assertEquals(RiskDecision.HIGH, signal.decision());
        assertEquals(45, signal.scoreContribution());
    }

    @Test
    void checkout_highLifetimeReturns_returnsMedium() {
        // totalReturns = 10 >= rateMinDenominator*2 (5*2=10), recent < repeatCountHigh
        when(returnRepo.countByUserId(USER_ID)).thenReturn(10L);
        when(returnRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(1L);
        RiskSignal signal = evaluator.evaluate(checkoutCtx());
        assertEquals(RiskDecision.MEDIUM, signal.decision());
        assertEquals(20, signal.scoreContribution());
    }

    @Test
    void checkout_moderateReturns_returnsLow() {
        // totalReturns=5, recent=1 (< repeatCountHigh=3), total < rateMinDenominator*2 (10)
        when(returnRepo.countByUserId(USER_ID)).thenReturn(5L);
        when(returnRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(1L);
        assertEquals(RiskDecision.LOW, evaluator.evaluate(checkoutCtx()).decision());
    }

    // ── RETURN mode ───────────────────────────────────────────────────────────

    @Test
    void returnMode_repeatReturnHigh_returnsHigh() {
        // recentReturns >= repeatCountHigh(3)
        when(returnRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(5L);
        RiskSignal signal = evaluator.evaluate(returnCtx(null, new BigDecimal("100")));
        assertEquals(RiskDecision.HIGH, signal.decision());
        assertEquals(50, signal.scoreContribution());
    }

    @Test
    void returnMode_fastReturnHighValue_returnsHigh() {
        // delivered 30 min ago (< fastMinutes=60), total $600 >= fastHighValueUsd=$500
        when(returnRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        Instant deliveredAt = Instant.now().minusSeconds(30 * 60);
        RiskSignal signal = evaluator.evaluate(returnCtx(deliveredAt, new BigDecimal("600")));
        assertEquals(RiskDecision.HIGH, signal.decision());
        assertEquals(55, signal.scoreContribution());
    }

    @Test
    void returnMode_fastReturnLowValue_returnsLow() {
        // delivered 30 min ago but total $100 < $500 threshold
        when(returnRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        Instant deliveredAt = Instant.now().minusSeconds(30 * 60);
        assertEquals(RiskDecision.LOW,
                evaluator.evaluate(returnCtx(deliveredAt, new BigDecimal("100"))).decision());
    }

    @Test
    void returnMode_slowReturn_returnsLow() {
        // delivered 2 days ago — well past fastMinutes threshold
        when(returnRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        Instant deliveredAt = Instant.now().minusSeconds(2 * 24 * 3600);
        assertEquals(RiskDecision.LOW,
                evaluator.evaluate(returnCtx(deliveredAt, new BigDecimal("1000"))).decision());
    }

    @Test
    void returnMode_nullDeliveredAt_fastCheckSkipped() {
        when(returnRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        assertEquals(RiskDecision.LOW,
                evaluator.evaluate(returnCtx(null, new BigDecimal("600"))).decision());
    }
}
