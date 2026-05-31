package backend.dtos.requests.product;

import backend.annotations.safeRichText.SafeRichText;
import backend.annotations.safeText.SafeText;
import backend.models.enums.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class CreateKitRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @SafeRichText
    @Size(max = 5000)
    private String description;

    @SafeText
    @Size(max = 500)
    private String thumbnailUrl;

    private BigDecimal compareAtPrice;

    @Size(min = 3, max = 3)
    private String currency = "USD";

    private boolean listed = true;

    private ProductStatus status = ProductStatus.DRAFT;

    @NotNull
    @Size(min = 1, max = 20)
    @Valid
    private List<KitSlotRequest> slots;
}
