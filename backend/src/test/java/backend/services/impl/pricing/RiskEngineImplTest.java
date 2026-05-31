package backend.services.impl.pricing;

import backend.configurations.environment.RiskProperties;
import backend.http.DeviceType;
import backend.models.core.RiskBlocklist;
import backend.models.enums.RiskAction;
import backend.models.enums.RiskAssessmentKind;
import backend.models.enums.RiskBlocklistType;
import backend.models.enums.RiskDecision;
import backend.models.enums.RiskMode;
import backend.models.enums.RiskSignalType;
import backend.repositories.RiskBlocklistRepository;
import backend.services.intf.auth.EmailVerificationService;
import backend.services.risk.RiskAssessmentResult;
import backend.services.risk.RiskContext;
import backend.services.risk.RiskSignal;
import backend.services.risk.evaluators.RiskRuleEvaluator;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskEngineImplTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID VIP_SEGMENT_ID = TestIds.uuid(2);

    private RiskBlocklistRepository riskBlocklistRepository;
    private EmailVerificationService emailVerificationService;
    private RiskProperties riskProperties;

    @BeforeEach
    void setUp() {
        riskBlocklistRepository = mock(RiskBlocklistRepository.class);
        emailVerificationService = mock(EmailVerificationService.class);
        riskProperties = new RiskProperties();
        riskProperties.setEnabled(true);
        riskProperties.setMode(RiskMode.SHADOW);
        riskProperties.setVerifyThreshold(20);
        riskProperties.setBlockThreshold(50);
    }

    @Test
    void assess_killSwitchReturnsAllowWithoutEvaluators() {
        riskProperties.setEnabled(false);
        RiskEngineImpl service = new RiskEngineImpl(
                riskBlocklistRepository,
                emailVerificationService,
                riskProperties,
                List.of(new StubEvaluator(RiskSignal.neutral(RiskSignalType.COUPON_ABUSE, "unused")))
        );

        RiskAssessmentResult result = service.assess(context(Set.of()));

        assertEquals(RiskAction.ALLOW, result.action());
        assertTrue(result.warnings().get(0).contains("kill switch"));
        assertTrue(result.signals().isEmpty());
        verify(riskBlocklistRepository, never()).findActive(any(RiskBlocklistType.class), any(String.class), any(Instant.class));
    }

    @Test
    void assess_vipReturnsAllowWithoutEvaluators() {
        riskProperties.setVipSegmentId(VIP_SEGMENT_ID.toString());
        RiskEngineImpl service = new RiskEngineImpl(
                riskBlocklistRepository,
                emailVerificationService,
                riskProperties,
                List.of(new StubEvaluator(RiskSignal.high(RiskSignalType.COUPON_ABUSE, 99, "unused")))
        );

        RiskAssessmentResult result = service.assess(context(Set.of(VIP_SEGMENT_ID)));

        assertEquals(RiskAction.ALLOW, result.action());
        assertTrue(result.warnings().get(0).contains("VIP allowlist"));
    }

    @Test
    void assess_blocklistShortCircuitsToBlock() {
        RiskContext ctx = context(Set.of());
        RiskBlocklist hit = new RiskBlocklist();
        hit.setId(TestIds.uuid(10));
        hit.setType(RiskBlocklistType.EMAIL);
        hit.setReason("Fraud ring");
        when(riskBlocklistRepository.findActive(RiskBlocklistType.EMAIL, "buyer@example.com", ctx.now()))
                .thenReturn(Optional.of(hit));

        RiskEngineImpl service = new RiskEngineImpl(
                riskBlocklistRepository,
                emailVerificationService,
                riskProperties,
                List.of(new StubEvaluator(RiskSignal.low(RiskSignalType.COUPON_ABUSE, "unused")))
        );

        RiskAssessmentResult result = service.assess(ctx);

        assertEquals(RiskAction.BLOCK, result.action());
        assertEquals(1000, result.totalScore());
        assertEquals(RiskSignalType.BLOCKLIST, result.signals().get(0).type());
    }

    @Test
    void assess_verifyDispatchesStepUpEmail() {
        RiskContext ctx = context(Set.of());
        stubNoBlocklistHits(ctx);

        RiskEngineImpl service = new RiskEngineImpl(
                riskBlocklistRepository,
                emailVerificationService,
                riskProperties,
                List.of(new StubEvaluator(RiskSignal.medium(RiskSignalType.COUPON_ABUSE, 25, "high coupon use")))
        );

        RiskAssessmentResult result = service.assess(ctx);

        assertEquals(RiskAction.VERIFY, result.action());
        verify(emailVerificationService).initiateVerification(USER_ID, "buyer@example.com");
    }

    @Test
    void assess_stepUpDispatchFailureAddsWarning() {
        RiskContext ctx = context(Set.of());
        stubNoBlocklistHits(ctx);
        doThrow(new RuntimeException("smtp down"))
                .when(emailVerificationService)
                .initiateVerification(USER_ID, "buyer@example.com");

        RiskEngineImpl service = new RiskEngineImpl(
                riskBlocklistRepository,
                emailVerificationService,
                riskProperties,
                List.of(new StubEvaluator(RiskSignal.medium(RiskSignalType.COUPON_ABUSE, 25, "high coupon use")))
        );

        RiskAssessmentResult result = service.assess(ctx);

        assertEquals(RiskAction.VERIFY, result.action());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("dispatch failed")));
    }

    @Test
    void assess_evaluatorReturningNullBecomesNeutralWarning() {
        RiskContext ctx = context(Set.of());
        stubNoBlocklistHits(ctx);

        RiskEngineImpl service = new RiskEngineImpl(
                riskBlocklistRepository,
                emailVerificationService,
                riskProperties,
                List.of(new NullEvaluator())
        );

        RiskAssessmentResult result = service.assess(ctx);

        assertEquals(RiskAction.ALLOW, result.action());
        assertEquals(RiskDecision.NEUTRAL, result.signals().get(0).decision());
        assertTrue(result.warnings().get(0).contains("treated as NEUTRAL"));
    }

    @Test
    void assess_evaluatorExceptionFailsOpenInShadowMode() {
        RiskContext ctx = context(Set.of());
        stubNoBlocklistHits(ctx);

        RiskEngineImpl service = new RiskEngineImpl(
                riskBlocklistRepository,
                emailVerificationService,
                riskProperties,
                List.of(new ThrowingEvaluator())
        );

        RiskAssessmentResult result = service.assess(ctx);

        assertEquals(RiskAction.ALLOW, result.action());
        assertEquals(RiskDecision.NEUTRAL, result.signals().get(0).decision());
        assertTrue(result.warnings().get(0).contains("fail-open"));
    }

    @Test
    void assess_evaluatorExceptionFailsToMediumInEnforceMode() {
        RiskContext ctx = context(Set.of());
        riskProperties.setMode(RiskMode.ENFORCE);
        stubNoBlocklistHits(ctx);

        RiskEngineImpl service = new RiskEngineImpl(
                riskBlocklistRepository,
                emailVerificationService,
                riskProperties,
                List.of(
                        new ThrowingEvaluator(),
                        new StubEvaluator(RiskSignal.medium(RiskSignalType.RETURN_PATTERN, 10, "returns"))
                )
        );

        RiskAssessmentResult result = service.assess(ctx);

        assertEquals(RiskAction.VERIFY, result.action());
        assertEquals(25, result.totalScore());
        assertEquals(RiskDecision.MEDIUM, result.signals().get(0).decision());
    }

    @Test
    void assess_blockThresholdWinsOverVerify() {
        RiskContext ctx = context(Set.of());
        stubNoBlocklistHits(ctx);

        RiskEngineImpl service = new RiskEngineImpl(
                riskBlocklistRepository,
                emailVerificationService,
                riskProperties,
                List.of(
                        new StubEvaluator(RiskSignal.medium(RiskSignalType.COUPON_ABUSE, 25, "coupon")),
                        new StubEvaluator(RiskSignal.high(RiskSignalType.RETURN_PATTERN, 30, "returns"))
                )
        );

        RiskAssessmentResult result = service.assess(ctx);

        assertEquals(RiskAction.BLOCK, result.action());
        assertEquals(55, result.totalScore());
    }

    private void stubNoBlocklistHits(RiskContext ctx) {
        when(riskBlocklistRepository.findActive(RiskBlocklistType.EMAIL, "buyer@example.com", ctx.now()))
                .thenReturn(Optional.empty());
        when(riskBlocklistRepository.findActive(RiskBlocklistType.IP, "203.0.113.10", ctx.now()))
                .thenReturn(Optional.empty());
        when(riskBlocklistRepository.findActive(RiskBlocklistType.DEVICE_FINGERPRINT, "device-1", ctx.now()))
                .thenReturn(Optional.empty());
    }

    private RiskContext context(Set<UUID> segments) {
        return new RiskContext(
                USER_ID,
                "buyer@example.com",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-05-18T00:00:00Z"),
                segments,
                TestIds.uuid(20),
                new BigDecimal("150.00"),
                null,
                "USD",
                "SAVE10",
                new BigDecimal("10.00"),
                List.of(TestIds.uuid(30)),
                "CA",
                "203.0.113.10",
                "device-1",
                "Mozilla/5.0",
                DeviceType.DESKTOP,
                RiskAssessmentKind.CHECKOUT,
                Instant.parse("2026-05-19T00:00:00Z")
        );
    }

    private static final class StubEvaluator implements RiskRuleEvaluator {
        private final RiskSignal signal;

        private StubEvaluator(RiskSignal signal) {
            this.signal = signal;
        }

        @Override
        public RiskSignalType type() {
            return signal.type();
        }

        @Override
        public RiskSignal evaluate(RiskContext ctx) {
            return signal;
        }
    }

    private static final class NullEvaluator implements RiskRuleEvaluator {
        @Override
        public RiskSignalType type() {
            return RiskSignalType.COUPON_ABUSE;
        }

        @Override
        public RiskSignal evaluate(RiskContext ctx) {
            return null;
        }
    }

    private static final class ThrowingEvaluator implements RiskRuleEvaluator {
        @Override
        public RiskSignalType type() {
            return RiskSignalType.MULTI_ACCOUNT_DEVICE_VELOCITY;
        }

        @Override
        public RiskSignal evaluate(RiskContext ctx) {
            throw new RuntimeException("evaluator down");
        }
    }
}
