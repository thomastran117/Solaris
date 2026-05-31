package backend.dtos.responses.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class KitSelectionResponse {
    private UUID slotId;
    private String slotName;
    private UUID productId;
    private String productName;
    private UUID variantId;
    private String variantTitle;
    private String variantSku;
    private int quantity;
    private BigDecimal unitPrice;
}
