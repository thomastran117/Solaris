package backend.dtos.responses.operations;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stockout snapshot. Two complementary numbers:
 *   - inventory side: {@code outOfStockRate = outOfStock / trackedProducts}
 *   - demand side:    {@code backorderRate  = ordersWithBackorders / totalOrders}
 *                     (computed over the lookback window)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockoutMetricResponse {
    private long trackedProducts;
    private long outOfStock;
    private double outOfStockRate;
    private long totalOrders;
    private long ordersWithBackorders;
    private double backorderRate;
}
