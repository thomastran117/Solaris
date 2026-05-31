package backend.dtos.requests.inventory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateInventorySettingsRequest {

    @Min(value = 0, message = "Low stock threshold must be 0 or greater")
    private Integer lowStockThreshold;

    @Min(value = 0, message = "Low stock threshold percent must be 0 or greater")
    @Max(value = 100, message = "Low stock threshold percent must be 100 or less")
    private Integer lowStockThresholdPercent;

    @Min(value = 1, message = "Max stock must be at least 1")
    private Integer maxStock;

    private Boolean autoRestockEnabled;

    @Min(value = 1, message = "Auto restock quantity must be at least 1")
    private Integer autoRestockQty;
}
