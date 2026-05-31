package backend.dtos.requests.profile;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {

    @SafeText
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @SafeText
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @Pattern(regexp = "^\\+?[0-9\\s\\-().]{7,30}$", message = "Invalid phone number format")
    private String phoneNumber;

    @SafeText
    @Size(max = 255, message = "Address must not exceed 255 characters")
    private String address;
}
