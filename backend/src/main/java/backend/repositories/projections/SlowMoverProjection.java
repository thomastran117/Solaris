package backend.repositories.projections;

import java.math.BigDecimal;

public interface SlowMoverProjection {
    /** Raw BINARY(16) product id — Spring Data cannot project byte[] to UUID directly. */
    byte[] getProductId();
    String getProductName();
    String getSku();
    Integer getCurrentStock();
    BigDecimal getPrice();
    String getCurrency();
    Long getTotalUnitsSold();
    BigDecimal getTotalRevenue();
}
