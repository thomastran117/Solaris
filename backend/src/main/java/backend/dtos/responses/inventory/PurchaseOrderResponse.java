package backend.dtos.responses.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class PurchaseOrderResponse {
    private UUID id;
    private UUID companyId;
    private UUID supplierId;
    private String supplierName;
    private String status;
    private LocalDate expectedArrivalDate;
    private String notes;
    private long totalCostCents;
    private UUID restockRequestId;
    private UUID createdByUserId;
    private Instant createdAt;
    private Instant sentAt;
    private Instant receivedAt;
    private Instant updatedAt;
    private List<PurchaseOrderItemResponse> items;
}
