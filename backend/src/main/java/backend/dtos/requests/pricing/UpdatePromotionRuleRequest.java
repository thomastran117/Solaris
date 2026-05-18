package backend.dtos.requests.pricing;

import com.fasterxml.jackson.databind.JsonNode;

import backend.annotations.safeRichText.SafeRichText;
import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** All fields nullable — null means "no change". ruleType is immutable after create. */
@Getter
@Setter
public class UpdatePromotionRuleRequest {

    @SafeText
    @Size(max = 255)
    private String name;

    @SafeRichText
    @Size(max = 500)
    private String description;

    /** When provided, the new JSON is validated against the current ruleType. */
    private JsonNode config;

    @Min(0)
    private Integer priority;

    private Boolean stackable;

    /** ACTIVE or DISABLED. EXPIRED is rejected (computed). */
    @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "status must be ACTIVE or DISABLED")
    private String status;

    private Instant startDate;
    private Instant endDate;

    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal minCartAmount;

    @Min(1)
    private Integer maxUses;

    @Min(1)
    private Integer maxUsesPerUser;

    private UUID fundedByCompanyId;

    /** When provided, fully replaces the product set. */
    private List<UUID> targetProductIds;

    /** When provided, fully replaces the bundle set. */
    private List<UUID> targetBundleIds;

    /** When provided, fully replaces the segment set. */
    private List<UUID> targetSegmentIds;
}
