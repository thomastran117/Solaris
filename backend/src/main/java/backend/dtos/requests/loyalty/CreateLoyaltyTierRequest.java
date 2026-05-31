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
public class CreateLoyaltyTierRequest {

    @SafeText
    @NotBlank
    @Size(max = 255)
    private String name;

    @Min(0)
    private long minPoints;

    @NotNull @DecimalMin("0.10") @DecimalMax("10.00")
    private BigDecimal earnMultiplier = BigDecimal.ONE;

    @Size(max = 4000)
    private String perksJson;

    @Size(max = 20)
    private String badgeColor;

    @Min(0)
    private int displayOrder = 0;
}
