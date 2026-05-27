package backend.dtos.requests.product;

import backend.annotations.safeText.SafeText;
import backend.models.enums.ProductStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class BatchUpdateProductsRequest {

    @NotEmpty(message = "IDs list must not be empty")
    @Size(max = 50, message = "Batch update is limited to 50 products per request")
    private List<UUID> ids;

    private ProductStatus status;

    private Boolean featured;

    private Boolean listed;

    @SafeText
    @Size(max = 255)
    private String category;

    @SafeText
    @Size(max = 100)
    private String brand;

    @AssertTrue(message = "At least one field to update must be provided")
    public boolean hasAtLeastOneField() {
        return status != null || featured != null || listed != null
                || category != null || brand != null;
    }
}
