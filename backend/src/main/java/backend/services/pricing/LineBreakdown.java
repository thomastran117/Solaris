package backend.services.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Per-line output of the engine. {@code effectiveLineTotal} is what the customer pays for
 * this line after all rule + coupon savings attributed to it.
 *
 * <p>Exactly one of {@code productId} / {@code bundleId} is non-null.
 *
 * @param index                stable 0-based position matching the request
 * @param productId            product id for the line; null for bundle lines
 * @param variantId            variant id, if the line targeted a variant; null for bundle lines
 * @param quantity             units ordered
 * @param unitBasePrice        pre-promotion unit price
 * @param savings              total savings attributed to this line (&ge; 0)
 * @param effectiveLineTotal   quantity * unitBasePrice − savings (never &lt; 0)
 * @param appliedRuleIds       ordered ids of rules that touched this line (deduped)
 * @param bundleId             bundle id for the line; null for product lines
 */
public record LineBreakdown(
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
