package backend.repositories.projections;

import java.math.BigDecimal;

/** Aggregate revenue row returned for a vendor within a time window. */
public interface VendorRevenueSummaryProjection {
    BigDecimal getTotalGross();
    BigDecimal getTotalCommission();
    BigDecimal getTotalNet();
    Long getTotalOrders();
    BigDecimal getAvgOrderValue();
}
