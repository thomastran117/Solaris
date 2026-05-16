package backend.dtos.requests.product;

import backend.annotations.safeRichText.SafeRichText;
import backend.annotations.safeText.SafeText;
import backend.models.enums.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class UpdateBundleRequest {

    @Size(max = 255)
    private String name;

    @SafeRichText
    @Size(max = 5000)
    private String description;

    @SafeText
    @Size(max = 500)
    private String thumbnailUrl;

    /** If null and items are being replaced, price is auto-recomputed from new items. */
    @DecimalMin("0.00")
    private BigDecimal price;

    private BigDecimal compareAtPrice;

    private ProductStatus status;

    private Boolean listed;

    /** If provided, replaces the entire items list. Max 10 items. */
    @Size(max = 10)
    @Valid
    private List<BundleItemRequest> items;
}
