package backend.dtos.responses.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class KitSlotChoiceResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private String productThumbnailUrl;
    private UUID variantId;
    private String variantTitle;
    private String variantSku;
    private BigDecimal basePrice;
    private BigDecimal priceDelta;
    private BigDecimal effectivePrice;
    private boolean defaultChoice;
    private int displayOrder;
    private boolean inStock;
}
