package backend.dtos.responses.sla;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VendorSLAMetricResponse {
    private UUID id;
    private UUID vendorId;
    private UUID marketplaceId;
    private LocalDate date;
    private long totalOrders;
    private Double shipHoursP50;
    private Double shipHoursP90;
    private double cancellationRate;
    private double refundRate;
    private double lateShipmentRate;
    private double defectRate;
    private Instant createdAt;
}
