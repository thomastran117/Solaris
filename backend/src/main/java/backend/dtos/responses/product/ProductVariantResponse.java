package backend.dtos.responses.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductVariantResponse(
        UUID id,
        String sku,
        BigDecimal price,
        BigDecimal compareAtPrice,
        Integer stock,
        Integer lowStockThreshold,
        boolean purchasable,
        boolean preorderEnabled,
        Instant preorderExpectedDate,
        String option1,
        String option2,
        String option3,
        String variantTitle,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {}
