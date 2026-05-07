package backend.dtos.requests.inventory;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SubscribeBackInStockRequest {

    @NotNull
    private Long productId;

    private Long variantId;
}
