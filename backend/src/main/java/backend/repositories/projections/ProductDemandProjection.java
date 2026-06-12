package backend.repositories.projections;

import java.math.BigDecimal;

/**
 * Lightweight projection for demand-tracking queries.
 * Unlike ProductSalesProjection, this omits stock fields — demand ranking
 * cares only about what sold, not what remains on the shelf.
 */
public interface ProductDemandProjection {
    /** Raw BINARY(16) product id — Spring Data cannot project byte[] to UUID directly. */
    byte[] getProductId();
    String getProductName();
    String getSku();
    BigDecimal getPrice();
    String getCurrency();
    Long getTotalUnitsSold();
    BigDecimal getTotalRevenue();
}
