package backend.dtos.responses.inventory;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class AdjustmentResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private UUID variantId;
    private UUID orderId;
    private UUID purchaseOrderId;
    private UUID adjustedByUserId;
    private int delta;
    private int previousStock;
    private int newStock;
    private String reason;
    private String note;
    private Instant createdAt;
}
