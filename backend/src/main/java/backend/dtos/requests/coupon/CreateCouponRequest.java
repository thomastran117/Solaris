package backend.dtos.requests.coupon;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class CreateCouponRequest {

    /**
     * Redemption code — uppercase, alphanumeric, dashes and underscores allowed (e.g. "SUMMER-20").
     * Globally unique. Immutable after creation.
     */
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Code must be uppercase letters, digits, dashes, or underscores")
    private String code;

    @NotBlank
    @Size(max = 255)
    private String name;

    /** "PERCENTAGE" or "FIXED_AMOUNT" — parsed to enum in service. */
    @NotBlank
    private String type;

    @jakarta.validation.constraints.NotNull
    @DecimalMin("0.01")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal value;

    /** Null = immediately effective. */
    private Instant startDate;

    /** Null = never expires. */
    private Instant endDate;

    /** Total redemption cap. Null = unlimited. */
    @Min(1)
    private Integer maxUses;

    /** Per-user redemption cap. Null = unlimited. */
    @Min(1)
    private Integer maxUsesPerUser;

    /** Minimum pre-coupon order total. Null = no minimum. */
    @DecimalMin("0.01")
    private BigDecimal minOrderAmount;
}
