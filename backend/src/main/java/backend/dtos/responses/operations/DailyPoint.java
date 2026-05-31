package backend.dtos.responses.operations;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One day on a dashboard time series. {@code value} is metric-specific (e.g.
 * average hours for duration metrics) and may be null on no-data days.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyPoint {
    private LocalDate day;
    private long count;
    private Double value;
}
