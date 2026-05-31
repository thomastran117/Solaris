package backend.repositories.projections;

import java.math.BigDecimal;
import java.util.UUID;

/** Top-product row for vendor analytics, aggregated by product. */
public interface VendorTopProductProjection {
    UUID getProductId();
    String getProductName();
    Long getTotalUnitsSold();
    BigDecimal getTotalRevenue();
}
