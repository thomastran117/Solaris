package backend.dtos.responses.sla;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VendorSLABreachResponse {
    private UUID id;
    private UUID vendorId;
    private UUID policyId;
    private String metric;
    private double actualValue;
    private double threshold;
    private Instant detectedAt;
    private Instant resolvedAt;
    private String actionTaken;
    private Instant notificationSentAt;
    private Instant createdAt;
}
