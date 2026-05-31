package backend.dtos.requests.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddProductImageRequest {

    @NotBlank(message = "Image URL is required")
    @Size(max = 500, message = "Image URL must not exceed 500 characters")
    private String imageUrl;
}
