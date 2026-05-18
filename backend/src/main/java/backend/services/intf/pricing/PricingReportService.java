package backend.services.intf.pricing;

import java.util.UUID;
import backend.dtos.responses.pricing.PayoutAttributionResponse;
import backend.dtos.responses.pricing.PromotionRuleAnalyticsResponse;

import java.time.Instant;

/**
 * Read-only analytics over the PromotionRedemption audit log. Used by admin and
 * company-owner dashboards to see what promotions actually cost and who they paid out.
 */
public interface PricingReportService {

    /** Platform-admin report aggregating savings by funding company. */
    PayoutAttributionResponse getPayoutAttribution(Instant from, Instant to);

    /**
     * Per-rule analytics scoped to a single promotion. Ownership is checked against
     * {@code ownerId}; throws {@code ForbiddenException} if the caller does not own
     * {@code companyId}.
     */
    PromotionRuleAnalyticsResponse getRuleAnalytics(
            UUID companyId, UUID ruleId, UUID ownerId, Instant from, Instant to);
}
