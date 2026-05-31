package backend.dtos.requests.collection;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Body for adding a product to a STATIC collection. Per-collection pin/boost are optional. */
@Getter
@Setter
public class AddCollectionProductRequest {

    @NotNull(message = "productId is required")
    private UUID productId;

    @Min(1)
    @Max(10)
    private Integer boostWeight;

    @Min(0)
    private Integer pinnedRank;
}
