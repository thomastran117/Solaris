package backend.dtos.responses.vendor;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class VendorAdjustmentResponse {
    private UUID id;
    private UUID vendorId;
    private long amountCents;
    private String currency;
    private String reason;
    private UUID createdByUserId;
    private UUID appliedToPayoutId;
    private Instant createdAt;
}
