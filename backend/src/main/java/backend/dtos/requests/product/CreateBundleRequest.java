package backend.dtos.requests.product;

import backend.annotations.safeRichText.SafeRichText;
import backend.annotations.safeText.SafeText;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class CreateBundleRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @SafeRichText
    @Size(max = 5000)
    private String description;

    @SafeText
    @Size(max = 500)
    private String thumbnailUrl;

    /** If null, auto-computed as sum of (item.quantity × product/variant price). */
    @DecimalMin("0.00")
    private BigDecimal price;

    private BigDecimal compareAtPrice;

    @Size(min = 3, max = 3)
    private String currency;

    private boolean listed = true;

    private boolean preorderEnabled = false;

    private Instant preorderExpectedDate;

    @NotNull
    @Size(min = 1, max = 10)
    @Valid
    private List<BundleItemRequest> items;
}
