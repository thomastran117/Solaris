package backend.services.risk.evaluators;

import backend.configurations.environment.RiskProperties;
import backend.http.DeviceType;
import backend.models.enums.RiskAssessmentKind;
import backend.models.enums.RiskDecision;
import backend.repositories.FailedPaymentAttemptRepository;
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

class FailedPaymentVelocityEvaluatorTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final String CLIENT_IP = "10.0.0.2";

    private FailedPaymentAttemptRepository failedPaymentRepo;
    private RiskProperties props;
    private FailedPaymentVelocityEvaluator evaluator;

    @BeforeEach
    void setUp() {
        failedPaymentRepo = mock(FailedPaymentAttemptRepository.class);
        props = new RiskProperties();
        evaluator = new FailedPaymentVelocityEvaluator(failedPaymentRepo, props);
    }

    private RiskContext ctx(String ip) {
        return new RiskContext(
                USER_ID, "buyer@example.com",
                Instant.now().minusSeconds(3600), null,
                Set.of(), null, new BigDecimal("100"), null,
                "USD", null, null,
                List.of(), "US", ip,
                null, "Mozilla/5.0", DeviceType.DESKTOP,
                RiskAssessmentKind.CHECKOUT, Instant.now());
    }

    // ── type() ────────────────────────────────────────────────────────────────

    @Test
    void type_returnsFailedPaymentVelocity() {
        assertEquals(backend.models.enums.RiskSignalType.FAILED_PAYMENT_VELOCITY, evaluator.type());
    }

    // ── Low / no failures ─────────────────────────────────────────────────────

    @Test
    void noFailures_returnsLow() {
        when(failedPaymentRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        when(failedPaymentRepo.countByIpAndCreatedAtAfter(eq(CLIENT_IP), any())).thenReturn(0L);
        RiskSignal signal = evaluator.evaluate(ctx(CLIENT_IP));
        assertEquals(RiskDecision.LOW, signal.decision());
        assertEquals(0, signal.scoreContribution());
    }

    // ── User medium ───────────────────────────────────────────────────────────

    @Test
    void userAtMediumThreshold_returnsMedium() {
        // default mediumCount = 2
        when(failedPaymentRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(2L);
        when(failedPaymentRepo.countByIpAndCreatedAtAfter(eq(CLIENT_IP), any())).thenReturn(0L);
        RiskSignal signal = evaluator.evaluate(ctx(CLIENT_IP));
        assertEquals(RiskDecision.MEDIUM, signal.decision());
        assertEquals(30, signal.scoreContribution());
    }

    @Test
    void userBetweenMediumAndHigh_returnsMedium() {
        when(failedPaymentRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(3L);
        when(failedPaymentRepo.countByIpAndCreatedAtAfter(eq(CLIENT_IP), any())).thenReturn(0L);
        RiskSignal signal = evaluator.evaluate(ctx(CLIENT_IP));
        assertEquals(RiskDecision.MEDIUM, signal.decision());
    }

    // ── User high ─────────────────────────────────────────────────────────────

    @Test
    void userAtHighThreshold_returnsHigh() {
        // default highCount = 5
        when(failedPaymentRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(5L);
        when(failedPaymentRepo.countByIpAndCreatedAtAfter(eq(CLIENT_IP), any())).thenReturn(0L);
        RiskSignal signal = evaluator.evaluate(ctx(CLIENT_IP));
        assertEquals(RiskDecision.HIGH, signal.decision());
        assertEquals(55, signal.scoreContribution());
    }

    // ── IP high ───────────────────────────────────────────────────────────────

    @Test
    void ipAtHighThreshold_returnsHigh() {
        // default ipHighCount = 10
        when(failedPaymentRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        when(failedPaymentRepo.countByIpAndCreatedAtAfter(eq(CLIENT_IP), any())).thenReturn(10L);
        RiskSignal signal = evaluator.evaluate(ctx(CLIENT_IP));
        assertEquals(RiskDecision.HIGH, signal.decision());
        assertEquals(60, signal.scoreContribution());
    }

    @Test
    void ipHighBeatsUserHigh_ipWins() {
        // IP check fires first and has higher score
        when(failedPaymentRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(5L);
        when(failedPaymentRepo.countByIpAndCreatedAtAfter(eq(CLIENT_IP), any())).thenReturn(10L);
        RiskSignal signal = evaluator.evaluate(ctx(CLIENT_IP));
        assertEquals(60, signal.scoreContribution()); // IP score (60) > user high (55)
    }

    // ── No IP in context ──────────────────────────────────────────────────────

    @Test
    void noClientIp_ipCheckSkipped() {
        when(failedPaymentRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        RiskSignal signal = evaluator.evaluate(ctx(null));
        assertEquals(RiskDecision.LOW, signal.decision());
        verify(failedPaymentRepo, never()).countByIpAndCreatedAtAfter(any(), any());
    }

    @Test
    void blankClientIp_ipCheckSkipped() {
        when(failedPaymentRepo.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        RiskSignal signal = evaluator.evaluate(ctx("  "));
        assertEquals(RiskDecision.LOW, signal.decision());
        verify(failedPaymentRepo, never()).countByIpAndCreatedAtAfter(any(), any());
    }
}
