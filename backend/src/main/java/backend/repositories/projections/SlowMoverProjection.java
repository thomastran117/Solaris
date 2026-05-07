package backend.repositories.projections;

import java.math.BigDecimal;

public interface SlowMoverProjection {
    Long getProductId();
    String getProductName();
    String getSku();
    Integer getCurrentStock();
    BigDecimal getPrice();
    String getCurrency();
    Long getTotalUnitsSold();
    BigDecimal getTotalRevenue();
}
