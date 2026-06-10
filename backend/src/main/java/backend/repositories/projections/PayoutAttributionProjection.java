package backend.repositories.projections;

import java.math.BigDecimal;

/**
 * Aggregated payout row per funding company across the PromotionRedemption audit log.
 * Feeds the admin settlement report at {@code GET /api/admin/pricing/payout-attribution}.
 */
public interface PayoutAttributionProjection {
    /** Raw BINARY(16) company id — Spring Data cannot project byte[] to UUID directly. */
    byte[] getFundedByCompanyId();
    String getCompanyName();
    BigDecimal getTotalSavings();
    Long getRedemptionCount();
    Long getUniqueOrderCount();
}
