package backend.dtos.requests.collection;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * Body for updating per-collection pin/boost values on an existing CollectionProduct row.
 * Both fields are nullable — sending {@code null} explicitly clears the override.
 */
@Getter
@Setter
public class UpdateCollectionProductRequest {

    @Min(1)
    @Max(10)
    private Integer boostWeight;

    @Min(0)
    private Integer pinnedRank;
}
