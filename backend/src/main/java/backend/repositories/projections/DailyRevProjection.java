package backend.repositories.projections;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailyRevProjection {
    LocalDate getDay();
    BigDecimal getTotalRevenue();
    Long getTotalUnits();
    Long getOrderCount();
}
