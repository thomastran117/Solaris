package backend.dtos.responses.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LineBreakdownResponse(
        int index,
        UUID productId,
        UUID variantId,
        int quantity,
        BigDecimal unitBasePrice,
        BigDecimal savings,
        BigDecimal effectiveLineTotal,
        List<UUID> appliedRuleIds,
        UUID bundleId
) {}
