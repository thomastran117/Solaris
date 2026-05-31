package backend.dtos.responses.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CommissionRecordResponse {
    private UUID id;
    private UUID subOrderId;
    private UUID vendorId;
    private UUID marketplaceId;
    private BigDecimal commissionRate;
    private BigDecimal grossAmount;
    private BigDecimal commissionAmount;
    private BigDecimal netVendorAmount;
    private String currency;
    private Instant computedAt;
}
