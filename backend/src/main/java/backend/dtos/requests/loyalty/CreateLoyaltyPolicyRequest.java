package backend.dtos.requests.loyalty;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class CreateLoyaltyPolicyRequest {

    @SafeText
    @NotBlank
    @Size(max = 255)
    private String name;

    @NotNull @DecimalMin("0.01") @DecimalMax("100.00")
    private BigDecimal earnRatePerDollar = BigDecimal.ONE;

    @Min(1) @Max(100)
    private int pointValueCents = 1;

    @Min(0)
    private int minRedemptionPoints = 100;

    @Min(1)
    private Integer pointsExpiryDays;

    @Min(0)
    private int birthdayBonusPoints = 0;

    @Min(0)
    private int birthdayBonusCreditCents = 0;

    @NotNull @DecimalMin("0.00") @DecimalMax("50.00")
    private BigDecimal cashbackRatePercent = BigDecimal.ZERO;

    @NotNull
    private String earnMode = "POINTS";
}
