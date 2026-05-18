package backend.dtos.responses.savedlist;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class SavedListItemResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private String productThumbnailUrl;
    private UUID variantId;
    private String variantSku;
    private int quantity;
    private String note;
    private boolean purchased;
    private Instant purchasedAt;
    private Instant addedAt;
}
