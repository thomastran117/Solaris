package backend.dtos.requests.segment;

import backend.annotations.safeRichText.SafeRichText;
import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCustomerSegmentRequest {

    @SafeText
    @Size(max = 100)
    private String name;

    @SafeRichText
    @Size(max = 500)
    private String description;
}
