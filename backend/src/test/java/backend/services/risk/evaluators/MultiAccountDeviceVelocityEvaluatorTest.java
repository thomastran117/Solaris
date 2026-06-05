package backend.services.risk.evaluators;

import backend.configurations.environment.RiskProperties;
import backend.http.DeviceType;
import backend.models.enums.RiskAssessmentKind;
import backend.models.enums.RiskDecision;
import backend.repositories.UserDeviceRepository;
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

class MultiAccountDeviceVelocityEvaluatorTest {

    private static final UUID USER_ID   = TestIds.uuid(1);
    private static final String FP      = "abc123fingerprintHashOf64chars___________________________XXXXXX";

    private UserDeviceRepository userDeviceRepo;
    private RiskProperties props;
    private MultiAccountDeviceVelocityEvaluator evaluator;

    @BeforeEach
    void setUp() {
        userDeviceRepo = mock(UserDeviceRepository.class);
        props = new RiskProperties();
        evaluator = new MultiAccountDeviceVelocityEvaluator(userDeviceRepo, props);
    }

    private RiskContext ctx(String fingerprint, Instant userCreatedAt) {
        return new RiskContext(
                USER_ID, "buyer@example.com",
                userCreatedAt, null, Set.of(),
                null, new BigDecimal("100"), null,
                "USD", null, null, List.of(),
                "US", "10.0.0.1", fingerprint, "Mozilla/5.0",
                DeviceType.DESKTOP, RiskAssessmentKind.CHECKOUT, Instant.now());
    }

    // ── type() ────────────────────────────────────────────────────────────────

    @Test
    void type_returnsMultiAccountDeviceVelocity() {
        assertEquals(backend.models.enums.RiskSignalType.MULTI_ACCOUNT_DEVICE_VELOCITY, evaluator.type());
    }

    // ── No fingerprint ────────────────────────────────────────────────────────

    @Test
    void nullFingerprint_returnsNeutral() {
        RiskSignal signal = evaluator.evaluate(ctx(null, Instant.now().minusSeconds(7200)));
        assertEquals(RiskDecision.NEUTRAL, signal.decision());
        verifyNoInteractions(userDeviceRepo);
    }

    @Test
    void blankFingerprint_returnsNeutral() {
        RiskSignal signal = evaluator.evaluate(ctx("  ", Instant.now().minusSeconds(7200)));
        assertEquals(RiskDecision.NEUTRAL, signal.decision());
        verifyNoInteractions(userDeviceRepo);
    }

    // ── Clean device, established account ────────────────────────────────────

    @Test
    void singleUser_noBurst_established_returnsLow() {
        when(userDeviceRepo.countDistinctUserIdByFingerprint(FP)).thenReturn(1L);
        when(userDeviceRepo.countByFingerprintAndCreatedAtAfter(eq(FP), any())).thenReturn(0L);
        Instant oldAccount = Instant.now().minusSeconds(7200); // 2 hours ago, well past 60-min threshold
        assertEquals(RiskDecision.LOW, evaluator.evaluate(ctx(FP, oldAccount)).decision());
    }

    // ── Bursting new account (no cross-account sharing) ──────────────────────

    @Test
    void singleUser_bursting_newAccount_returnsMediumWithBonus() {
        when(userDeviceRepo.countDistinctUserIdByFingerprint(FP)).thenReturn(1L);
        when(userDeviceRepo.countByFingerprintAndCreatedAtAfter(eq(FP), any())).thenReturn(3L); // burst
        Instant newAccount = Instant.now().minusSeconds(30); // 30 seconds old
        RiskSignal signal = evaluator.evaluate(ctx(FP, newAccount));
        assertEquals(RiskDecision.MEDIUM, signal.decision());
        assertEquals(25, signal.scoreContribution()); // burst(15) + new(10)
    }

    @Test
    void singleUser_bursting_oldAccount_returnsMediumBurstOnly() {
        when(userDeviceRepo.countDistinctUserIdByFingerprint(FP)).thenReturn(1L);
        when(userDeviceRepo.countByFingerprintAndCreatedAtAfter(eq(FP), any())).thenReturn(3L);
        Instant oldAccount = Instant.now().minusSeconds(7200);
        RiskSignal signal = evaluator.evaluate(ctx(FP, oldAccount));
        assertEquals(RiskDecision.MEDIUM, signal.decision());
        assertEquals(15, signal.scoreContribution()); // burst only
    }

    // ── Medium distinct users ─────────────────────────────────────────────────

    @Test
    void mediumDistinctUsers_noBurst_returnsMedium() {
        // default distinctUsersMedium = 2
        when(userDeviceRepo.countDistinctUserIdByFingerprint(FP)).thenReturn(2L);
        when(userDeviceRepo.countByFingerprintAndCreatedAtAfter(eq(FP), any())).thenReturn(0L);
        Instant oldAccount = Instant.now().minusSeconds(7200);
        RiskSignal signal = evaluator.evaluate(ctx(FP, oldAccount));
        assertEquals(RiskDecision.MEDIUM, signal.decision());
        assertEquals(20, signal.scoreContribution()); // base 20, no bonus
    }

    @Test
    void mediumDistinctUsers_withBurst_scoreIncludes15Bonus() {
        when(userDeviceRepo.countDistinctUserIdByFingerprint(FP)).thenReturn(2L);
        when(userDeviceRepo.countByFingerprintAndCreatedAtAfter(eq(FP), any())).thenReturn(2L); // burst
        Instant oldAccount = Instant.now().minusSeconds(7200);
        RiskSignal signal = evaluator.evaluate(ctx(FP, oldAccount));
        assertEquals(RiskDecision.MEDIUM, signal.decision());
        assertEquals(35, signal.scoreContribution()); // 20 + burst(15)
    }

    // ── High distinct users ───────────────────────────────────────────────────

    @Test
    void highDistinctUsers_noBurst_returnsHigh() {
        // default distinctUsersHigh = 4
        when(userDeviceRepo.countDistinctUserIdByFingerprint(FP)).thenReturn(4L);
        when(userDeviceRepo.countByFingerprintAndCreatedAtAfter(eq(FP), any())).thenReturn(0L);
        Instant oldAccount = Instant.now().minusSeconds(7200);
        RiskSignal signal = evaluator.evaluate(ctx(FP, oldAccount));
        assertEquals(RiskDecision.HIGH, signal.decision());
        assertEquals(50, signal.scoreContribution()); // base 50, no bonus
    }

    @Test
    void highDistinctUsers_withBurstAndNewAccount_scoreIncludesFullBonus() {
        when(userDeviceRepo.countDistinctUserIdByFingerprint(FP)).thenReturn(5L);
        when(userDeviceRepo.countByFingerprintAndCreatedAtAfter(eq(FP), any())).thenReturn(3L); // burst
        Instant newAccount = Instant.now().minusSeconds(30);
        RiskSignal signal = evaluator.evaluate(ctx(FP, newAccount));
        assertEquals(RiskDecision.HIGH, signal.decision());
        assertEquals(75, signal.scoreContribution()); // 50 + 15 + 10
    }
}
