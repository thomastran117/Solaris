package backend.dtos.responses.marketplace;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CommissionPolicyResponse {
    private UUID id;
    private UUID marketplaceId;
    private String name;
    private BigDecimal defaultRate;
    private Instant effectiveFrom;
    private Instant effectiveTo;
    private boolean active;
    private List<RuleResponse> rules;
    private Instant createdAt;
    private Instant updatedAt;

    @Getter
    @AllArgsConstructor
    public static class RuleResponse {
        private UUID id;
        private String ruleType;
        private String matchValue;
        private BigDecimal rate;
        private int priority;
    }
}
