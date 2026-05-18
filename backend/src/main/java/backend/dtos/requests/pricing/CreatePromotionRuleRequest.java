package backend.dtos.requests.pricing;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreatePromotionRuleRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @backend.annotations.safeRichText.SafeRichText
    @Size(max = 500)
    private String description;

    /** PERCENTAGE_OFF, FIXED_OFF, BOGO, TIERED_PRICE, FREE_SHIPPING. */
    @NotBlank
    private String ruleType;

    /** Per-type configuration JSON. Shape validated against ruleType in service. */
    @NotNull
    private JsonNode config;

    /** Lower = applied first. Default 100. */
    @Min(0)
    private Integer priority;

    /** When true, stacks on top of other stackable rules. Default false. */
    private Boolean stackable;

    /** Null = immediately effective. */
    private Instant startDate;

    /** Null = never expires. */
    private Instant endDate;

    /** Minimum pre-promotion subtotal. Null = no minimum. */
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal minCartAmount;

    @Min(1)
    private Integer maxUses;

    @Min(1)
    private Integer maxUsesPerUser;

    /** Platform-admin-only. Null = company funds the promotion itself. */
    private UUID fundedByCompanyId;

    /** Empty or null = applies to entire company catalogue. */
    private List<UUID> targetProductIds;

    /** Empty or null = not bundle-scoped. */
    private List<UUID> targetBundleIds;

    /** Empty or null = applies to all users including anonymous. */
    private List<UUID> targetSegmentIds;
}
