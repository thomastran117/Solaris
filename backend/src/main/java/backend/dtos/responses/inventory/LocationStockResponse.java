package backend.dtos.responses.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class LocationStockResponse {
    private UUID id;
    private UUID locationId;
    private String locationName;
    private UUID productId;
    private UUID variantId;          // null when product-level
    private int stock;
    private Integer lowStockThreshold;
    private String stockStatus;      // IN_STOCK | LOW_STOCK | OUT_OF_STOCK
    private Instant updatedAt;
}
