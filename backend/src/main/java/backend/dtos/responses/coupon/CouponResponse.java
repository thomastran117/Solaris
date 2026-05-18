package backend.dtos.responses.coupon;

import backend.models.enums.DiscountStatus;
import backend.models.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CouponResponse(
        UUID id,
        UUID companyId,
        String code,
        String name,
        DiscountType type,
        BigDecimal value,
        DiscountStatus status,
        Instant startDate,
        Instant endDate,
        Integer maxUses,
        int usedCount,
        Integer maxUsesPerUser,
        BigDecimal minOrderAmount,
        Instant createdAt,
        Instant updatedAt
) {}
