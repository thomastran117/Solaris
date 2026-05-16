package backend.dtos.requests.vendor;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitVendorTaxRequest {

    @SafeText
    @NotBlank(message = "Tax ID is required")
    @Size(max = 100, message = "Tax ID must not exceed 100 characters")
    private String taxId;

    @SafeText
    @NotBlank(message = "Legal business name is required")
    @Size(max = 255, message = "Legal business name must not exceed 255 characters")
    private String legalBusinessName;

    @SafeText
    @NotBlank(message = "Business address is required")
    @Size(max = 500, message = "Business address must not exceed 500 characters")
    private String businessAddress;

    @SafeText
    @NotBlank(message = "Country is required")
    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String country;
}
