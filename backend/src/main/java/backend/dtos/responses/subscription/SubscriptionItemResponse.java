package backend.dtos.responses.subscription;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionItemResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private UUID variantId;
    private int quantity;
    private long unitPriceCents;
}
