package backend.services.risk;

import java.util.UUID;
import backend.http.DeviceType;
import backend.models.enums.RiskAssessmentKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Immutable input passed to the risk engine. All fields the evaluators need must live here —
 * evaluators never make HTTP calls or pull from {@code SecurityContext} / request scope themselves.
 *
 * @param userId              authenticated buyer id (required)
 * @param userEmail           buyer email (for blocklist + step-up email dispatch)
 * @param userCreatedAt       account creation timestamp (for new-account heuristics)
 * @param userLastLoginAt     most recent successful login (nullable)
 * @param userSegmentIds      buyer's customer segments; VIP allowlist check lives in the engine
 * @param orderId             nullable on checkout (order is already persisted when we assess)
 * @param orderTotal          order total in {@link #currency}
 * @param orderDeliveredAt    delivery timestamp — populated for RETURN-mode assessments (null on CHECKOUT)
 * @param currency            ISO 4217 code; informational
 * @param couponCode          applied coupon code, or null
 * @param couponDiscountAmount monetary value of the coupon discount (in {@link #currency}), or null
 * @param productCompanyIds   seller companies represented in the cart (for per-merchant policy later)
 * @param shippingCountry     ISO country code or null — consumed by the stubbed GeoIP evaluator
 * @param clientIp            resolved client IP from {@code ClientRequestFilter}
 * @param deviceFingerprint   64-char device fingerprint hash, or null for anonymous/unknown
 * @param userAgent           raw UA string (already sanitised to ≤512 chars)
 * @param deviceType          classified device type
 * @param kind                which flow is calling the engine — CHECKOUT or RETURN
 * @param now                 evaluation time (for window-based signals)
 */
public record RiskContext(
        UUID userId,
        String userEmail,
        Instant userCreatedAt,
        Instant userLastLoginAt,
        Set<UUID> userSegmentIds,
        UUID orderId,
        BigDecimal orderTotal,
        Instant orderDeliveredAt,
        String currency,
        String couponCode,
        BigDecimal couponDiscountAmount,
        List<UUID> productCompanyIds,
        String shippingCountry,
        String clientIp,
        String deviceFingerprint,
        String userAgent,
        DeviceType deviceType,
        RiskAssessmentKind kind,
        Instant now
) {}
