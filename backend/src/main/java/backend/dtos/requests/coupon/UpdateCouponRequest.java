package backend.dtos.requests.coupon;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class UpdateCouponRequest {

    /** Null = no change. Code is immutable — not updateable. */
    @Size(max = 255)
    private String name;

    /** "PERCENTAGE" or "FIXED_AMOUNT". Null = no change. */
    private String type;

    @DecimalMin("0.01")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal value;

    /** "ACTIVE" or "DISABLED" only. "EXPIRED" is rejected with 400. Null = no change. */
    private String status;

    private Instant startDate;

    private Instant endDate;

    /** Null = no change. */
    @Min(1)
    private Integer maxUses;

    /** Null = no change. */
    @Min(1)
    private Integer maxUsesPerUser;

    /** Null = no change. */
    @DecimalMin("0.01")
    private BigDecimal minOrderAmount;
}
