package backend.dtos.requests.product;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class BatchDeleteProductsRequest {

    @NotEmpty(message = "IDs list must not be empty")
    @Size(max = 50, message = "Batch delete is limited to 50 products per request")
    private List<UUID> ids;
}
