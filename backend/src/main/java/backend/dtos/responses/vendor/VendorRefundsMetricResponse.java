package backend.dtos.responses.vendor;

import backend.dtos.responses.operations.DailyPoint;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class VendorRefundsMetricResponse {
    private long totalReturns;
    private long totalOrders;
    private double refundRate;
    private List<DailyPoint> daily;
}
