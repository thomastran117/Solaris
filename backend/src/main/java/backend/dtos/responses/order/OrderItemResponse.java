package backend.dtos.responses.order;

import backend.models.enums.FulfillmentMethod;
import backend.models.enums.FulfillmentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class OrderItemResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private UUID variantId;
    private String variantTitle;
    private String variantSku;
    private int quantity;
    private BigDecimal unitPrice;
    private UUID fulfillmentLocationId;
    private String fulfillmentLocationName;
    private FulfillmentStatus fulfillmentStatus;
    private UUID bundleId;
    private String bundleName;
    private UUID kitId;
    private String kitName;
    private List<KitSelectionResponse> kitSelections;
    private BigDecimal discountAmount;
    private FulfillmentMethod fulfillmentMethod;
}
