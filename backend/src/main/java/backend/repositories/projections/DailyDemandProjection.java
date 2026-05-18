package backend.repositories.projections;

import java.time.LocalDate;
import java.util.UUID;

public interface DailyDemandProjection {
    UUID getProductId();
    LocalDate getDay();
    Long getUnits();
}
