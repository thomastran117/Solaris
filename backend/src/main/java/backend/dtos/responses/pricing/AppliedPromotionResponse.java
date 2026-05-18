package backend.dtos.responses.pricing;

import backend.models.enums.PromotionRuleType;

import java.math.BigDecimal;
import java.util.UUID;

public record AppliedPromotionResponse(
        UUID ruleId,
        String name,
        PromotionRuleType ruleType,
        BigDecimal savings,
        UUID fundedByCompanyId
) {}
