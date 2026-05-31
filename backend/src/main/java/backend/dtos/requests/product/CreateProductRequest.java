package backend.dtos.requests.product;

import backend.annotations.safeIdentifier.SafeIdentifier;
import backend.annotations.safeRichText.SafeRichText;
import backend.annotations.safeText.SafeText;
import backend.models.enums.ProductStatus;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    @SafeText
    private String name;

    @Size(max = 10000, message = "Description must not exceed 10000 characters")
    @SafeRichText
    private String description;

    @Size(max = 100, message = "SKU must not exceed 100 characters")
    @SafeIdentifier
    private String sku;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Price must be 0.00 or greater")
    @Digits(integer = 10, fraction = 2, message = "Price must have at most 10 integer digits and 2 decimal places")
    private BigDecimal price;

    @DecimalMin(value = "0.00", inclusive = true, message = "Compare-at price must be 0.00 or greater")
    @Digits(integer = 10, fraction = 2, message = "Compare-at price must have at most 10 integer digits and 2 decimal places")
    private BigDecimal compareAtPrice;

    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency;

    @Size(max = 100, message = "Category must not exceed 100 characters")
    @SafeText
    private String category;

    @Size(max = 100, message = "Brand must not exceed 100 characters")
    @SafeText
    private String brand;

    @Size(max = 500, message = "Tags must not exceed 500 characters")
    @SafeRichText
    private String tags;

    @Size(max = 500, message = "Thumbnail URL must not exceed 500 characters")
    private String thumbnailUrl;

    @Min(value = 0, message = "Stock must be 0 or greater")
    private Integer stock;

    @DecimalMin(value = "0.0", inclusive = true, message = "Weight must be 0 or greater")
    @Digits(integer = 7, fraction = 3, message = "Weight must have at most 7 integer digits and 3 decimal places")
    private BigDecimal weight;

    @Size(max = 10, message = "Weight unit must not exceed 10 characters")
    @SafeText
    private String weightUnit;

    private boolean featured = false;

    private boolean purchasable = true;

    private boolean listed = true;

    /**
     * Optional initial lifecycle status. When omitted, the service defaults to {@link ProductStatus#DRAFT}.
     * If set to {@link ProductStatus#SCHEDULED}, {@link #scheduledPublishAt} must also be supplied
     * and be in the future.
     */
    private ProductStatus status;

    /** Required (and must be in the future) when {@link #status} is {@link ProductStatus#SCHEDULED}. */
    private Instant scheduledPublishAt;

    @AssertTrue(message = "compareAtPrice must be greater than price when both are provided")
    public boolean isCompareAtPriceValid() {
        if (price == null || compareAtPrice == null) return true;
        return compareAtPrice.compareTo(price) > 0;
    }
}
