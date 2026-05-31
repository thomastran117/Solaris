package backend.dtos.requests.product;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    private String name;

    private String description;

    @Size(max = 100, message = "SKU must not exceed 100 characters")
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
    private String category;

    @Size(max = 100, message = "Brand must not exceed 100 characters")
    private String brand;

    @Size(max = 500, message = "Tags must not exceed 500 characters")
    private String tags;

    @Size(max = 500, message = "Thumbnail URL must not exceed 500 characters")
    private String thumbnailUrl;

    @Min(value = 0, message = "Stock must be 0 or greater")
    private Integer stock;

    @DecimalMin(value = "0.0", inclusive = true, message = "Weight must be 0 or greater")
    @Digits(integer = 7, fraction = 3, message = "Weight must have at most 7 integer digits and 3 decimal places")
    private BigDecimal weight;

    @Size(max = 10, message = "Weight unit must not exceed 10 characters")
    private String weightUnit;

    private boolean featured = false;
}
