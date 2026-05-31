package backend.repositories.projections;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Interface projection for native SQL analytics queries that aggregate
 * order_items data per product. Spring Data JPA maps column aliases
 * (case-insensitive) to the getter names declared here.
 */
public interface ProductSalesProjection {
    UUID getProductId();
    String getProductName();
    String getSku();
    Integer getCurrentStock();
    BigDecimal getPrice();
    String getCurrency();
    Long getTotalUnitsSold();
    BigDecimal getTotalRevenue();
}
