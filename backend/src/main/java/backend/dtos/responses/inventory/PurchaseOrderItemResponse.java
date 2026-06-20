package backend.dtos.responses.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class PurchaseOrderItemResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private UUID variantId;
    private String variantSku;
    private int orderedQty;
    private int receivedQty;
    private long unitCostCents;
    private UUID locationId;
    private String locationName;
}
