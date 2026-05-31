package backend.repositories.projections;

import java.time.LocalDate;

/** One day of an SLA duration metric (count + avg seconds). */
public interface DailyDurationProjection {
    LocalDate getDay();
    Long getCount();
    Double getAvgSeconds();
}
