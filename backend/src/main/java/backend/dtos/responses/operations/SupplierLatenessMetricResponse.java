package backend.dtos.responses.operations;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Supplier on-time-delivery metric. {@code daily} reports the count of late
 * receipts per day (not total receipts) so the chart highlights problem days.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplierLatenessMetricResponse {
    private long total;
    private long late;
    private double lateRate;
    private Double avgLateDays;
    private List<DailyPoint> daily;
}
