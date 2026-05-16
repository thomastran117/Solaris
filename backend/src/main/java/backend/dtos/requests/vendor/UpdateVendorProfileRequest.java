package backend.dtos.requests.vendor;

import backend.annotations.safeRichText.SafeRichText;
import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateVendorProfileRequest {

    @SafeText
    @Size(max = 255, message = "Display name must not exceed 255 characters")
    private String displayName;

    @SafeRichText
    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @Email(message = "Support email must be a valid email address")
    @Size(max = 255, message = "Support email must not exceed 255 characters")
    private String supportEmail;

    @Size(max = 30, message = "Support phone must not exceed 30 characters")
    private String supportPhone;

    @Size(max = 255, message = "Website must not exceed 255 characters")
    private String website;
}
