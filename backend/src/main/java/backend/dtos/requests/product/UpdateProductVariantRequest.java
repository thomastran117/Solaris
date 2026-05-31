package backend.dtos.requests.product;

import backend.annotations.safeIdentifier.SafeIdentifier;
import backend.annotations.safeText.SafeText;
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

    private BigDecimal price;

    private BigDecimal compareAtPrice;

    private Integer stock;

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
}
