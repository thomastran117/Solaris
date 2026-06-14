package backend.dtos.requests.product;

import backend.annotations.safeIdentifier.SafeIdentifier;
import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateProductVariantRequest {

    @Size(max = 100, message = "SKU must be at most 100 characters")
    @SafeIdentifier
    private String sku;

    @DecimalMin(value = "0.00", inclusive = true, message = "Price must be 0.00 or greater")
    @Digits(integer = 10, fraction = 2, message = "Price must have at most 10 integer digits and 2 decimal places")
    private BigDecimal price;

    @DecimalMin(value = "0.00", inclusive = true, message = "Compare-at price must be 0.00 or greater")
    @Digits(integer = 10, fraction = 2, message = "Compare-at price must have at most 10 integer digits and 2 decimal places")
    private BigDecimal compareAtPrice;

    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stock;

    @Min(value = 0, message = "Low stock threshold must be 0 or greater")
    private Integer lowStockThreshold;

    private Boolean purchasable;

    private Boolean preorderEnabled;

    private java.time.Instant preorderExpectedDate;

    @Size(max = 100)
    @SafeText
    private String option1;

    @Size(max = 100)
    @SafeText
    private String option2;

    @Size(max = 100)
    @SafeText
    private String option3;

    private Integer displayOrder;

    @AssertTrue(message = "compareAtPrice must be greater than price when both are provided")
    public boolean isCompareAtPriceValid() {
        if (price == null || compareAtPrice == null) return true;
        return compareAtPrice.compareTo(price) > 0;
    }
}
