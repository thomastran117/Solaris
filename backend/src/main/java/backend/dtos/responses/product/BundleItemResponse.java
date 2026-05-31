package backend.dtos.responses.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class BundleItemResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private UUID variantId;
    private String variantSku;
    private String variantTitle;
    private int quantity;
    private int displayOrder;
}
