package backend.dtos.requests.tax;

import backend.annotations.safeIdentifier.SafeIdentifier;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Admin request to create a tax rate. {@code state}/{@code postalCode} default to the empty-string
 * wildcard when omitted (country-/state-level default). Rate is fractional, e.g. 0.08875 = 8.875%.
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateTaxRateRequest {

    @NotBlank(message = "country is required")
    @Size(min = 2, max = 2, message = "country must be a 2-letter ISO code")
    private String country;

    @Size(max = 2, message = "state must be a 2-letter code (or omitted for a country default)")
    private String state;

    @SafeIdentifier
    @Size(max = 20, message = "postalCode must be at most 20 characters")
    private String postalCode;

    @NotNull(message = "rate is required")
    @DecimalMin(value = "0.0", message = "rate must be >= 0")
    private BigDecimal rate;

    private boolean shippingTaxable;

    /** Null defaults to active. */
    private Boolean active;

    @Size(max = 255)
    private String description;
}
