package backend.dtos.responses.vendor;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VendorPayoutsMetricResponse {
    private UUID vendorId;
    private BigDecimal totalPaidOut;
    private long totalPayouts;
    private List<PayoutSummary> recent;

    public record PayoutSummary(
            UUID payoutId,
            BigDecimal netAmount,
            String currency,
            String status,
            Instant paidAt
    ) {}
}
