package backend.repositories.projections;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lightweight projection for demand-tracking queries.
 * Unlike ProductSalesProjection, this omits stock fields — demand ranking
 * cares only about what sold, not what remains on the shelf.
 */
public interface ProductDemandProjection {
    UUID getProductId();
    String getProductName();
    String getSku();
    BigDecimal getPrice();
    String getCurrency();
    Long getTotalUnitsSold();
    BigDecimal getTotalRevenue();
}
