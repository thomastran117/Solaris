package backend.dtos.requests.marketplace;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class CreateCommissionPolicyRequest {

    @NotBlank(message = "Policy name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @NotNull(message = "Default rate is required")
    @DecimalMin(value = "0.0000", message = "Rate must be 0 or greater")
    @DecimalMax(value = "1.0000", message = "Rate must not exceed 1.0 (100%)")
    private BigDecimal defaultRate;

    private Instant effectiveFrom;
    private Instant effectiveTo;
    private boolean active = true;
    private List<RuleRequest> rules;

    @Getter
    @Setter
    public static class RuleRequest {
        @NotBlank private String ruleType;
        @NotBlank @Size(max = 255) private String matchValue;
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") private BigDecimal rate;
        private int priority = 0;
    }
}
