package backend.dtos.responses.inventory;

import backend.models.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class InventoryTransferResponse {
    private UUID id;
    private UUID companyId;
    private UUID productId;
    private String productName;
    private UUID fromLocationId;
    private String fromLocationName;
    private UUID toLocationId;
    private String toLocationName;
    private int quantity;
    private TransferStatus status;
    private String notes;
    private UUID createdByUserId;
    private UUID receivedByUserId;
    private UUID cancelledByUserId;
    private Instant createdAt;
    private Instant inTransitAt;
    private Instant receivedAt;
    private Instant cancelledAt;
}
