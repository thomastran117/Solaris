package backend.repositories.projections;

import java.math.BigDecimal;

public interface CategorySalesProjection {
    String getCategory();
    BigDecimal getTotalRevenue();
    Long getTotalUnits();
    Long getOrderCount();
}
