package backend.services.pricing;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Immutable input to PricingEngine.quote. Everything the engine needs to evaluate rules:
 * cart lines, who the buyer is (for segment gating + per-user usage), an optional coupon,
 * shipping (pass-through for FREE_SHIPPING), and "now" (for time-window filtering).
 *
 * @param lines           resolved cart lines with server-authoritative prices
 * @param userId          null for anonymous — segment-gated rules are skipped
 * @param userSegmentIds  empty for anonymous; ids of segments the user belongs to
 * @param currency        ISO 4217 code; not used for conversion but passed through to the response
 * @param couponCode      optional redemption code — applied after rule pass
 * @param shippingAmount  pre-discount shipping cost; null treated as zero
 * @param now             evaluation time for time-window filtering
 */
public record CartContext(
        List<CartLine> lines,
        UUID userId,
        Set<UUID> userSegmentIds,
        String currency,
        String couponCode,
        BigDecimal shippingAmount,
        Instant now
) {}
