package backend.dtos.requests.inventory;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class SubscribeBackInStockRequest {

    @NotNull
    private UUID productId;

    private UUID variantId;
}
