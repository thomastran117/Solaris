package backend.dtos.requests.vendor;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenerateStripeOnboardingLinkRequest {

    @NotBlank(message = "Return URL is required")
    private String returnUrl;

    @NotBlank(message = "Refresh URL is required")
    private String refreshUrl;
}
