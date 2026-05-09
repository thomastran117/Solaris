package backend.dtos.responses.savedlist;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class SavedListItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productThumbnailUrl;
    private Long variantId;
    private String variantSku;
    private int quantity;
    private String note;
    private boolean purchased;
    private Instant purchasedAt;
    private Instant addedAt;
}
