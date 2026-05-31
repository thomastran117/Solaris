package backend.repositories.projections;

import java.time.LocalDate;

/** One day of a count-only time series (e.g. cancellations per day). */
public interface DailyCountProjection {
    LocalDate getDay();
    Long getCount();
}
