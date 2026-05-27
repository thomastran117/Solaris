package backend.dtos.requests.product;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class KitSlotChoiceRequest {

    @NotNull
    private UUID productId;

    private UUID variantId;

    private BigDecimal priceDelta;

    private boolean defaultChoice = false;

    private int displayOrder = 0;
}
