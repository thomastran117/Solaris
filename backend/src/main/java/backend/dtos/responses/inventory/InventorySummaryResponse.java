package backend.dtos.responses.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class InventorySummaryResponse {
    private long totalProducts;
    private long trackedProducts;
    private long inStockCount;
    private long lowStockCount;
    private long outOfStockCount;
    private BigDecimal totalInventoryValue;
}
