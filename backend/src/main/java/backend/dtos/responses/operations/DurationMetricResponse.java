package backend.dtos.responses.operations;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Used for fulfillment, refund resolution, and warehouse pick-delay metrics.
 * Average is in hours (converted server-side from native seconds).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DurationMetricResponse {
    private long count;
    private Double avgHours;
    private List<DailyPoint> daily;
}
