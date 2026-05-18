package backend.services.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Internal mutable per-line state threaded through evaluators. Not exposed in the API;
 * converted to an immutable {@link LineBreakdown} at the end of {@code PricingEngineImpl.compute}.
 *
 * <p>Evaluators read {@link #unitBasePrice()} / {@link #quantity()} / {@link #remaining()} and
 * accumulate via {@link #applySavings(long, java.math.BigDecimal)} and the BOGO counters.
 */
public final class WorkingLine {

    private final CartLine source;
    /** Remaining currency to take savings from on this line. Starts at qty * basePrice, floors at 0. */
    private BigDecimal remaining;
    private BigDecimal savings = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    /** Ordered, deduped ids of rules that applied to this line. */
    private final Set<UUID> appliedRuleIds = new LinkedHashSet<>();
    /** BOGO trigger units already consumed on this line (prevents double-counting across overlapping rules). */
    private int bogoTriggerConsumed = 0;
    /** BOGO reward units already consumed on this line. */
    private int bogoRewardConsumed = 0;

    public WorkingLine(CartLine source) {
        this.source = source;
        this.remaining = source.unitBasePrice().multiply(BigDecimal.valueOf(source.quantity()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public CartLine source()                  { return source; }
    public int index()                        { return source.index(); }
    public UUID productId()                   { return source.productId(); }
    public UUID variantId()                   { return source.variantId(); }
    public int quantity()                     { return source.quantity(); }
    public BigDecimal unitBasePrice()         { return source.unitBasePrice(); }
    public UUID companyId()                   { return source.companyId(); }
    public UUID bundleId()                    { return source.bundleId(); }
    public BigDecimal remaining()             { return remaining; }
    public BigDecimal savings()               { return savings; }
    public int bogoTriggerConsumed()          { return bogoTriggerConsumed; }
    public int bogoRewardConsumed()           { return bogoRewardConsumed; }
    public int bogoTriggerAvailable()         { return source.quantity() - bogoTriggerConsumed - bogoRewardConsumed; }
    public int bogoRewardAvailable()          { return source.quantity() - bogoRewardConsumed - bogoTriggerConsumed; }

    /** Apply a positive saving to this line. Silently capped at {@link #remaining()}. Records the rule id. */
    public BigDecimal applySavings(UUID ruleId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal capped = amount.min(remaining).setScale(2, RoundingMode.HALF_UP);
        if (capped.signum() <= 0) return BigDecimal.ZERO;
        remaining = remaining.subtract(capped);
        savings = savings.add(capped);
        if (ruleId != null) {
            appliedRuleIds.add(ruleId);
        }
        return capped;
    }

    public void consumeBogoTrigger(int units) { this.bogoTriggerConsumed += units; }
    public void consumeBogoReward(int units)  { this.bogoRewardConsumed  += units; }

    public LineBreakdown toBreakdown() {
        return new LineBreakdown(
                source.index(),
                source.productId(),
                source.variantId(),
                source.quantity(),
                source.unitBasePrice(),
                savings,
                remaining,
                new ArrayList<>(appliedRuleIds),
                source.bundleId()
        );
    }
}
