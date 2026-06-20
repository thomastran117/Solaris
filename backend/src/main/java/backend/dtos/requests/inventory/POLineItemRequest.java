package backend.dtos.requests.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class POLineItemRequest {

    @NotNull
    private UUID productId;

    private UUID variantId;

    @NotNull
    @Min(1)
    private Integer orderedQty;

    @NotNull
    @Min(0)
    private Long unitCostCents;

    private UUID locationId;
}
