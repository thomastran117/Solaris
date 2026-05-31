package backend.dtos.requests.product;

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
public class BatchUpdateBundlesRequest {

    @NotEmpty(message = "IDs list must not be empty")
    @Size(max = 50, message = "Batch update is limited to 50 bundles per request")
    private List<UUID> ids;

    private ProductStatus status;

    private Boolean listed;

    @AssertTrue(message = "At least one field to update must be provided")
    public boolean hasAtLeastOneField() {
        return status != null || listed != null;
    }
}
