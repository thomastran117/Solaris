package backend.repositories.projections;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Daily revenue breakdown row for a vendor analytics time series. */
public interface VendorRevenueDailyProjection {
    LocalDate getDay();
    BigDecimal getGross();
    BigDecimal getCommission();
    BigDecimal getNet();
    Long getOrderCount();
}
