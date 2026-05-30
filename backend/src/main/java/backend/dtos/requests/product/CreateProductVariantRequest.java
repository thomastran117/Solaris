package backend.dtos.requests.product;

import backend.annotations.safeIdentifier.SafeIdentifier;
import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateProductVariantRequest {

    @Size(max = 100, message = "SKU must be at most 100 characters")
    @SafeIdentifier
    private String sku;

    @NotNull(message = "Price is required")
    private BigDecimal price;

    private BigDecimal compareAtPrice;

    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stock;

    private Integer lowStockThreshold;

    private boolean purchasable = true;

    @Size(max = 100)
    @SafeText
    private String option1;

    @Size(max = 100)
    @SafeText
    private String option2;

    @Size(max = 100)
    @SafeText
    private String option3;

    private int displayOrder = 0;

    @AssertTrue(message = "compareAtPrice must be greater than price when both are provided")
    public boolean isCompareAtPriceValid() {
        if (price == null || compareAtPrice == null) return true;
        return compareAtPrice.compareTo(price) > 0;
    }
}
