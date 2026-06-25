package backend.dtos.requests.tax;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Admin patch request for a tax rate. Only non-null fields are applied. Jurisdiction
 * ({@code country}/{@code state}/{@code postalCode}) is intentionally immutable here — create a new
 * rate instead — so a rate's identity can't silently change underneath existing order snapshots.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateTaxRateRequest {

    @DecimalMin(value = "0.0", message = "rate must be >= 0")
    private BigDecimal rate;

    private Boolean shippingTaxable;

    private Boolean active;

    private String description;
}
