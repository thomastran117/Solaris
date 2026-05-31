package backend.dtos.requests.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Body for {@code PATCH /companies/{cId}/products/{pId}/merchandising}.
 *
 * <p>All fields are nullable on the wire. The semantics are:
 * <ul>
 *   <li>Field absent (JSON key missing) → leave the existing value alone.</li>
 *   <li>Field present and explicitly {@code null} → clear the value.</li>
 * </ul>
 * Distinguishing these requires the caller to use a JSON merge-patch shape; on the
 * server we treat all incoming {@code null}s as "clear" since {@code PATCH} requests
 * for this endpoint only carry the keys the merchant actually wants to change.
 */
@Getter
@Setter
public class UpdateProductMerchandisingRequest {

    @Min(value = 1, message = "boostWeight must be between 1 and 10")
    @Max(value = 10, message = "boostWeight must be between 1 and 10")
    private Integer boostWeight;

    /** Must be in the future when present. Validated at the service layer for clearer message. */
    private Instant pinnedUntil;

    @Min(value = 0, message = "pinnedRank must be 0 or greater")
    private Integer pinnedRank;
}
