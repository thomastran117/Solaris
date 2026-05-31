package backend.dtos.requests.risk;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Body for the merchant approve/reject risk-review endpoints. The note is persisted on the review. */
@Getter
@Setter
public class RiskDecisionRequest {

    @Size(max = 500, message = "Merchant note must be 500 characters or fewer")
    private String merchantNote;
}
