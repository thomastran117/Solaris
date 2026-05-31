package backend.dtos.requests.marketplace;

import backend.models.enums.PayoutSchedule;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMarketplaceRequest {

    @NotBlank(message = "Slug is required")
    @Size(min = 3, max = 100, message = "Slug must be 3–100 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug may only contain lowercase letters, digits, and hyphens")
    private String slug;

    private PayoutSchedule payoutSchedule = PayoutSchedule.WEEKLY;

    @Min(value = 0, message = "Hold period must be 0 or more days")
    @Max(value = 90, message = "Hold period cannot exceed 90 days")
    private int holdPeriodDays = 7;

    @Size(max = 3, message = "Currency must be a 3-letter ISO code")
    private String defaultCurrency = "USD";

    private boolean acceptingApplications = true;
}
