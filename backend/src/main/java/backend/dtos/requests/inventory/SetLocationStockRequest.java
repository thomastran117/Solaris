package backend.dtos.requests.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetLocationStockRequest {

    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock must be >= 0")
    private Integer stock;

    @Min(value = 0, message = "Low stock threshold must be >= 0")
    private Integer lowStockThreshold;
}
