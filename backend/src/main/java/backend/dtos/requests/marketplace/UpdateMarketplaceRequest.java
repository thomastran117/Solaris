package backend.dtos.requests.marketplace;

import backend.models.enums.PayoutSchedule;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMarketplaceRequest {

    private PayoutSchedule payoutSchedule;

    @Min(value = 0, message = "Hold period must be 0 or more days")
    @Max(value = 90, message = "Hold period cannot exceed 90 days")
    private Integer holdPeriodDays;

    private Boolean acceptingApplications;
}
