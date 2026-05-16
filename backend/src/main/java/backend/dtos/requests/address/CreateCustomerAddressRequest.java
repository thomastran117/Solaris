package backend.dtos.requests.address;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateCustomerAddressRequest {

    @SafeText
    @NotBlank
    @Size(max = 50)
    private String label;

    @SafeText
    @NotBlank
    @Size(max = 150)
    private String recipientName;

    @SafeText
    @NotBlank
    @Size(max = 255)
    private String street;

    @SafeText
    @Size(max = 255)
    private String street2;

    @SafeText
    @NotBlank
    @Size(max = 100)
    private String city;

    @SafeText
    @NotBlank
    @Size(max = 100)
    private String state;

    @NotBlank
    @Size(max = 20)
    private String postalCode;

    @NotBlank
    @Pattern(regexp = "^[A-Z]{2}$", message = "country must be a 2-letter ISO 3166-1 alpha-2 code")
    private String country;

    @Size(max = 30)
    private String phoneNumber;

    private boolean isDefault = false;
}
