package backend.services.risk.evaluators;

import backend.http.DeviceType;
import backend.models.enums.RiskAssessmentKind;
import backend.models.enums.RiskDecision;
import backend.services.risk.RiskContext;
import backend.testutil.TestIds;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShippingIpCountryMismatchEvaluatorTest {

    private final ShippingIpCountryMismatchEvaluator evaluator = new ShippingIpCountryMismatchEvaluator();

    private RiskContext ctx(String shippingCountry, String ip) {
        return new RiskContext(
                TestIds.uuid(1), "buyer@example.com",
                Instant.now().minusSeconds(3600), null,
                Set.of(), null, new BigDecimal("100"), null,
                "USD", null, null, List.of(),
                shippingCountry, ip, null, "Mozilla/5.0",
                DeviceType.DESKTOP, RiskAssessmentKind.CHECKOUT, Instant.now());
    }

    @Test
    void type_returnsShippingIpCountryMismatch() {
        assertEquals(backend.models.enums.RiskSignalType.SHIPPING_IP_COUNTRY_MISMATCH, evaluator.type());
    }

    @Test
    void alwaysReturnsNeutral_matchingCountry() {
        assertEquals(RiskDecision.NEUTRAL, evaluator.evaluate(ctx("US", "10.0.0.1")).decision());
    }

    @Test
    void alwaysReturnsNeutral_mismatchCountry() {
        assertEquals(RiskDecision.NEUTRAL, evaluator.evaluate(ctx("CN", "10.0.0.1")).decision());
    }

    @Test
    void alwaysReturnsNeutral_noIp() {
        assertEquals(RiskDecision.NEUTRAL, evaluator.evaluate(ctx("US", null)).decision());
    }

    @Test
    void scoreContribution_isZero() {
        assertEquals(0, evaluator.evaluate(ctx("US", "1.2.3.4")).scoreContribution());
    }
}
