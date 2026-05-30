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
public class UpdateProductRequest {

    @Size(max = 255, message = "Product name must not exceed 255 characters")
    @SafeText
    private String name;

    @Size(max = 10000, message = "Description must not exceed 10000 characters")
    @SafeRichText
    private String description;

    @Size(max = 100, message = "SKU must not exceed 100 characters")
    @SafeIdentifier
    private String sku;

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

    private ProductStatus status;

    /**
     * Required (and must be in the future) when {@link #status} is set to
     * {@link ProductStatus#SCHEDULED}. Cleared automatically when status moves to any other value.
     */
    private Instant scheduledPublishAt;

    private Boolean featured;

    private Boolean purchasable;

    private Boolean listed;

    private Boolean preorderEnabled;

    private Instant preorderExpectedDate;

    /**
     * Merchandising — folded into the main update payload so the admin editor can save in one go.
     * The dedicated {@code /merchandising} endpoint (see {@code UpdateProductMerchandisingRequest})
     * remains available for headless clients that only want to touch these fields.
     *
     * <p>{@code boostWeight} valid range is 1–10. {@code pinnedUntil} must be in the future when
     * set. Setting {@code pinnedRank} without {@code pinnedUntil} is treated as a no-op on the
     * service side (the rank is cleared alongside the window).
     */
    @Min(value = 1, message = "boostWeight must be between 1 and 10")
    @Max(value = 10, message = "boostWeight must be between 1 and 10")
    private Integer boostWeight;

    private Instant pinnedUntil;

    @Min(value = 0, message = "pinnedRank must be 0 or greater")
    private Integer pinnedRank;

    @AssertTrue(message = "compareAtPrice must be greater than price when both are provided")
    public boolean isCompareAtPriceValid() {
        if (price == null || compareAtPrice == null) return true;
        return compareAtPrice.compareTo(price) > 0;
    }

    @AssertTrue(message = "preorderExpectedDate is required and must be in the future when preorderEnabled is true")
    public boolean isPreorderValid() {
        if (!Boolean.TRUE.equals(preorderEnabled)) return true;
        return preorderExpectedDate != null && preorderExpectedDate.isAfter(Instant.now());
    }
}
