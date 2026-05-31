package backend.repositories.projections;

import java.math.BigDecimal;

/** Marketplace-level GMV / commission aggregate row. */
public interface MarketplaceSummaryProjection {
    BigDecimal getGmv();
    BigDecimal getTotalCommission();
    Long getActiveVendors();
    Long getTotalOrders();
}
