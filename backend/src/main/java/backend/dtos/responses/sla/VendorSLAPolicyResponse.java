package backend.dtos.responses.sla;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VendorSLAPolicyResponse {
    private UUID id;
    private UUID marketplaceId;
    private String name;
    private double targetShipHours;
    private double targetResponseHours;
    private double maxCancellationRate;
    private double maxRefundRate;
    private double maxLateShipmentRate;
    private String breachAction;
    private int evaluationWindowDays;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
