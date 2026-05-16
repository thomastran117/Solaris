package backend.dtos.requests.subscription;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShippingAddressRequest {

    @SafeText
    @NotBlank
    @Size(max = 150)
    private String name;

    @SafeText
    @NotBlank
    @Size(max = 255)
    private String street;

    @SafeText
    @NotBlank
    @Size(max = 100)
    private String city;

    @SafeText
    @Size(max = 100)
    private String state;

    @NotBlank
    @Size(max = 20)
    private String postalCode;

    @NotBlank
    @Size(min = 2, max = 3)
    private String country;
}
