package backend.repositories.projections;

import java.time.LocalDate;

public interface DailyDemandProjection {
    /** Raw BINARY(16) product id — Spring Data cannot project byte[] to UUID directly. */
    byte[] getProductId();
    LocalDate getDay();
    Long getUnits();
}
