package backend.dtos.responses.vendor;

import backend.dtos.responses.operations.DailyPoint;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class VendorOrdersMetricResponse {
    private long total;
    private long cancelled;
    private double cancellationRate;
    private List<DailyPoint> daily;
}
