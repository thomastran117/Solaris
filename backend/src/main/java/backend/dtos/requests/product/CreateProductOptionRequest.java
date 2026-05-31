package backend.dtos.requests.product;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateProductOptionRequest {

    @NotBlank(message = "Option name is required")
    @Size(max = 100, message = "Option name must be at most 100 characters")
    @SafeText
    private String name;
}
