package backend.dtos.requests.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BundleItemRequest {

    @NotNull
    private UUID productId;

    private UUID variantId;

    @Min(1)
    @Max(999)
    private int quantity = 1;

    private int displayOrder = 0;
}
