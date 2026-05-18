package backend.dtos.responses.vendor;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VendorPayoutItemResponse {
    private UUID id;
    private UUID subOrderId;
    private UUID commissionRecordId;
    private UUID adjustmentId;
    private String entryType;
    private BigDecimal grossAmount;
    private BigDecimal commissionAmount;
    private BigDecimal netAmount;
}
