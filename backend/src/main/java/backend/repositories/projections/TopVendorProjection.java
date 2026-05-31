package backend.repositories.projections;

import java.math.BigDecimal;
import java.util.UUID;

/** Top-vendor row for the marketplace analytics cross-vendor leaderboard. */
public interface TopVendorProjection {
    UUID getVendorId();
    String getVendorName();
    Long getTotalSubOrders();
    BigDecimal getTotalGrossRevenue();
    BigDecimal getTotalCommission();
    Double getCancellationRate();
}
