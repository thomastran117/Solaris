package backend.dtos.requests.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BatchCreateProductsRequest {

    @NotEmpty(message = "Products list must not be empty")
    @Size(max = 50, message = "Batch create is limited to 50 products per request")
    @Valid
    private List<CreateProductRequest> products;
}
