package backend.dtos.responses.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class InventoryItemResponse {
    private UUID productId;
    private String productName;
    private String sku;
    private Integer stock;
    private Integer lowStockThreshold;
    private Integer lowStockThresholdPercent;
    private Integer maxStock;
    private boolean autoRestockEnabled;
    private Integer autoRestockQty;
    private boolean lowStock;
    private boolean outOfStock;
    private String stockStatus;
    private BigDecimal price;
    private String currency;
    private Instant updatedAt;
}
