package backend.services.risk.evaluators;

import backend.models.enums.RiskSignalType;
import backend.services.risk.RiskContext;
import backend.services.risk.RiskSignal;
import org.springframework.stereotype.Component;

/**
 * STUB — preserves the wiring point for a future GeoIP-backed evaluator.
 *
 * <p>Real implementation will resolve {@link RiskContext#clientIp()} to a country via
 * MaxMind / IPinfo and compare against {@link RiskContext#shippingCountry()}. For this
 * PR the signal is always NEUTRAL so it contributes 0 to the score and surfaces a
 * warning in the assessment reasons instead.
 */
@Component
public class ShippingIpCountryMismatchEvaluator implements RiskRuleEvaluator {

    @Override
    public RiskSignalType type() {
        return RiskSignalType.SHIPPING_IP_COUNTRY_MISMATCH;
    }

    @Override
    public RiskSignal evaluate(RiskContext ctx) {
        return RiskSignal.neutral(type(), "GeoIP lookup not configured");
    }
}
