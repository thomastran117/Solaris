package backend.dtos.responses.pricing;

import com.fasterxml.jackson.databind.JsonNode;

import backend.models.enums.DiscountStatus;
import backend.models.enums.PromotionRuleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PromotionRuleResponse(
        UUID id,
        UUID companyId,
        String name,
        String description,
        PromotionRuleType ruleType,
        JsonNode config,
        DiscountStatus status,
        int priority,
        boolean stackable,
        Instant startDate,
        Instant endDate,
        BigDecimal minCartAmount,
        Integer maxUses,
        int usedCount,
        Integer maxUsesPerUser,
        UUID fundedByCompanyId,
        List<UUID> targetProductIds,
        List<UUID> targetBundleIds,
        List<UUID> targetSegmentIds,
        Instant createdAt,
        Instant updatedAt
) {}
