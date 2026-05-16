package backend.dtos.requests.marketplace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VendorAdjustmentRequest {

    /** Positive = credit to vendor, negative = debit from vendor. Zero is invalid. */
    @NotNull(message = "Amount in cents is required")
    private Long amountCents;

    @jakarta.validation.constraints.AssertTrue(message = "Amount in cents must not be zero")
    public boolean isAmountCentsNonZero() {
        return amountCents == null || amountCents != 0L;
    }

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency;

    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
