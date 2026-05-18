package backend.repositories.projections;

import java.math.BigDecimal;
import java.util.UUID;

public interface SlowMoverProjection {
    UUID getProductId();
    String getProductName();
    String getSku();
    Integer getCurrentStock();
    BigDecimal getPrice();
    String getCurrency();
    Long getTotalUnitsSold();
    BigDecimal getTotalRevenue();
}
