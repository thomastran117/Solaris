package backend.dtos.requests.segment;

import backend.annotations.safeRichText.SafeRichText;
import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCustomerSegmentRequest {

    /** Uppercased, alphanumeric + underscore/dash. Immutable after create. */
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$")
    private String code;

    @SafeText
    @NotBlank
    @Size(max = 100)
    private String name;

    @SafeRichText
    @Size(max = 500)
    private String description;
}
