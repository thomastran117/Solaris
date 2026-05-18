package backend.repositories.projections;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Aggregated payout row per funding company across the PromotionRedemption audit log.
 * Feeds the admin settlement report at {@code GET /api/admin/pricing/payout-attribution}.
 */
public interface PayoutAttributionProjection {
    UUID getFundedByCompanyId();
    String getCompanyName();
    BigDecimal getTotalSavings();
    Long getRedemptionCount();
    Long getUniqueOrderCount();
}
