package backend.services.impl.pricing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.models.core.Coupon;
import backend.models.core.Product;
import backend.models.core.ProductBundle;
import backend.models.core.PromotionRule;
import backend.models.enums.DiscountStatus;
import backend.models.enums.DiscountType;
import backend.models.enums.PromotionRuleType;
import backend.repositories.CouponRepository;
import backend.repositories.PromotionPerUserCountRepository;
import backend.repositories.PromotionRuleRepository;
import backend.services.intf.pricing.PricingEngine;
import backend.services.pricing.AppliedPromotion;
import backend.services.pricing.CartContext;
import backend.services.pricing.CartLine;
import backend.services.pricing.LineBreakdown;
import backend.services.pricing.PricingResult;
import backend.services.pricing.WorkingLine;
import backend.services.pricing.config.FreeShippingConfig;
import backend.services.pricing.config.PromotionConfigValidator;
import backend.services.pricing.evaluators.RuleEvaluator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Single-pass pricing engine.
 *
 * <p>Flow:
 * <ol>
 *   <li>Build {@link WorkingLine}s from {@code ctx.lines}; compute subtotal.
 *   <li>Load ACTIVE, in-window candidate rules for the cart's companies.
 *   <li>Filter each candidate by segment gate, product gate, min-cart threshold.
 *   <li>Sort by (priority ASC, non-stackables first, id ASC).
 *   <li>Apply rules in order; non-stackable rules are mutually exclusive — the first
 *       one to fire blocks further non-stackable rules.
 *   <li>Apply coupon (if supplied) on the post-rule subtotal.
 *   <li>Build immutable result.
 * </ol>
 *
 * <p>The engine is deliberately side-effect free: it never writes back usage counters or
 * redemptions. Those are the caller's responsibility (order flow) so a preview call
 * can't consume a coupon.
 */
@Service
public class PricingEngineImpl implements PricingEngine {

    private final PromotionRuleRepository ruleRepository;
    private final CouponRepository couponRepository;
    private final PromotionConfigValidator configValidator;
    private final PromotionPerUserCountRepository promotionPerUserCountRepository;
    private final Map<backend.models.enums.PromotionRuleType, RuleEvaluator> evaluators;

    public PricingEngineImpl(
            PromotionRuleRepository ruleRepository,
            CouponRepository couponRepository,
            PromotionConfigValidator configValidator,
            PromotionPerUserCountRepository promotionPerUserCountRepository,
            List<RuleEvaluator> evaluatorList) {
        this.ruleRepository = ruleRepository;
        this.couponRepository = couponRepository;
        this.configValidator = configValidator;
        this.promotionPerUserCountRepository = promotionPerUserCountRepository;

        Map<backend.models.enums.PromotionRuleType, RuleEvaluator> map =
                new EnumMap<>(backend.models.enums.PromotionRuleType.class);
        for (RuleEvaluator ev : evaluatorList) {
            map.put(ev.type(), ev);
        }
        this.evaluators = map;
    }

    @Override
    @Transactional(readOnly = true)
    public PricingResult quote(CartContext ctx) {
        if (ctx.lines() == null || ctx.lines().isEmpty()) {
            return emptyResult(ctx);
        }

        Set<Long> companyIds = ctx.lines().stream()
                .map(CartLine::companyId)
                .collect(Collectors.toCollection(HashSet::new));
        List<PromotionRule> candidates = ruleRepository.findActiveCandidates(companyIds, ctx.now());
        Coupon coupon = resolveCoupon(ctx.couponCode());
        return compute(ctx, candidates, coupon);
    }

    /**
     * Package-private entry point used by tests to bypass the repository. The real
     * {@link #quote(CartContext)} delegates here after loading rules + coupon.
     */
    PricingResult compute(CartContext ctx, Collection<PromotionRule> candidateRules, Coupon coupon) {
        List<String> warnings = new ArrayList<>();

        List<WorkingLine> lines = new ArrayList<>(ctx.lines().size());
        for (CartLine cl : ctx.lines()) {
            lines.add(new WorkingLine(cl));
        }
        BigDecimal subtotal = lines.stream()
                .map(WorkingLine::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        List<PromotionRule> eligible = filterAndSort(candidateRules, ctx, subtotal, warnings);

        List<AppliedPromotion> applied = new ArrayList<>();
        BigDecimal promotionSavings = BigDecimal.ZERO;
        BigDecimal remainingShipping = ctx.shippingAmount() != null
                ? ctx.shippingAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal shippingSavings = BigDecimal.ZERO;
        boolean nonStackableFired = false;

        for (PromotionRule rule : eligible) {
            if (!rule.isStackable() && nonStackableFired) continue;

            if (rule.getRuleType() == PromotionRuleType.FREE_SHIPPING) {
                BigDecimal saving = applyFreeShipping(rule, lines, remainingShipping, warnings);
                if (saving.signum() <= 0) continue;
                shippingSavings = shippingSavings.add(saving);
                remainingShipping = remainingShipping.subtract(saving).max(BigDecimal.ZERO);
                applied.add(new AppliedPromotion(
                        rule.getId(),
                        rule.getName(),
                        rule.getRuleType(),
                        saving,
                        rule.getFundedByCompany() != null
                                ? rule.getFundedByCompany().getId()
                                : rule.getCompany().getId()));
                if (!rule.isStackable()) nonStackableFired = true;
                continue;
            }

            RuleEvaluator evaluator = evaluators.get(rule.getRuleType());
            if (evaluator == null) {
                warnings.add("No evaluator for rule type " + rule.getRuleType() + " (id=" + rule.getId() + ")");
                continue;
            }
            List<WorkingLine> eligibleLines = linesForRule(rule, lines);
            if (eligibleLines.isEmpty()) continue;

            Object parsedConfig = configValidator.parseStored(rule.getRuleType(), rule.getConfigJson());
            BigDecimal saving = evaluator.apply(rule, parsedConfig, eligibleLines);
            if (saving == null || saving.signum() <= 0) continue;

            promotionSavings = promotionSavings.add(saving);
            applied.add(new AppliedPromotion(
                    rule.getId(),
                    rule.getName(),
                    rule.getRuleType(),
                    saving.setScale(2, RoundingMode.HALF_UP),
                    rule.getFundedByCompany() != null
                            ? rule.getFundedByCompany().getId()
                            : rule.getCompany().getId()));

            if (!rule.isStackable()) nonStackableFired = true;
        }

        BigDecimal postPromotionSubtotal = subtotal.subtract(promotionSavings).max(BigDecimal.ZERO);
        BigDecimal couponSavings = BigDecimal.ZERO;
        String appliedCouponCode = null;
        if (coupon != null) {
            CouponOutcome outcome = applyCoupon(coupon, postPromotionSubtotal, lines, ctx, warnings);
            couponSavings = outcome.saving();
            if (couponSavings.signum() > 0) {
                appliedCouponCode = coupon.getCode();
            }
        }

        BigDecimal finalTotal = subtotal
                .subtract(promotionSavings)
                .subtract(couponSavings)
                .max(BigDecimal.ZERO)
                .add(remainingShipping)
                .setScale(2, RoundingMode.HALF_UP);

        List<LineBreakdown> breakdowns = lines.stream().map(WorkingLine::toBreakdown).toList();
        return new PricingResult(
                breakdowns,
                List.copyOf(applied),
                subtotal,
                promotionSavings.setScale(2, RoundingMode.HALF_UP),
                couponSavings.setScale(2, RoundingMode.HALF_UP),
                appliedCouponCode,
                remainingShipping,
                shippingSavings.setScale(2, RoundingMode.HALF_UP),
                finalTotal,
                List.copyOf(warnings));
    }

    /**
     * FREE_SHIPPING is special-cased: it reduces the shipping line, not item subtotals, so
     * it doesn't flow through the {@link RuleEvaluator} contract. Returns the positive
     * shipping reduction (scale 2, HALF_UP) or zero if the rule is inapplicable.
     */
    private BigDecimal applyFreeShipping(
            PromotionRule rule,
            List<WorkingLine> lines,
            BigDecimal remainingShipping,
            List<String> warnings) {
        if (remainingShipping.signum() <= 0) return BigDecimal.ZERO;
        List<WorkingLine> eligibleLines = linesForRule(rule, lines);
        if (eligibleLines.isEmpty()) return BigDecimal.ZERO;

        FreeShippingConfig cfg = (FreeShippingConfig) configValidator
                .parseStored(rule.getRuleType(), rule.getConfigJson());

        if (cfg.requiresAllTargetProducts()
                && rule.getTargetProducts() != null
                && !rule.getTargetProducts().isEmpty()) {
            Set<Long> cartProductIds = lines.stream()
                    .filter(l -> l.productId() != null)
                    .map(WorkingLine::productId)
                    .collect(Collectors.toSet());
            boolean allPresent = rule.getTargetProducts().stream()
                    .allMatch(p -> cartProductIds.contains(p.getId()));
            if (!allPresent) {
                warnings.add("Rule '" + rule.getName() + "' skipped: requires all target products in cart");
                return BigDecimal.ZERO;
            }
        }

        BigDecimal cap = cfg.maxShippingDiscount() != null
                ? cfg.maxShippingDiscount() : remainingShipping;
        return cap.min(remainingShipping).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private List<PromotionRule> filterAndSort(
            Collection<PromotionRule> candidates,
            CartContext ctx,
            BigDecimal subtotal,
            List<String> warnings) {
        List<PromotionRule> kept = new ArrayList<>();
        if (candidates == null) return kept;

        for (PromotionRule rule : candidates) {
            if (rule.getStatus() != DiscountStatus.ACTIVE) continue;
            if (!withinWindow(rule, ctx.now())) continue;

            if (!segmentGateOk(rule, ctx)) continue;

            if (rule.getMinCartAmount() != null && subtotal.compareTo(rule.getMinCartAmount()) < 0) {
                warnings.add("Rule '" + rule.getName() + "' skipped: cart subtotal below minCartAmount");
                continue;
            }

            if (rule.getMaxUsesPerUser() != null && ctx.userId() != null) {
                int used = promotionPerUserCountRepository.countByRuleIdAndUserId(rule.getId(), ctx.userId());
                if (used >= rule.getMaxUsesPerUser()) {
                    warnings.add("Rule '" + rule.getName() + "' skipped: per-user limit reached");
                    continue;
                }
            }

            kept.add(rule);
        }

        kept.sort(Comparator
                .comparingInt(PromotionRule::getPriority)
                .thenComparing((PromotionRule r) -> r.isStackable() ? 1 : 0)
                .thenComparing(PromotionRule::getId));
        return kept;
    }

    private boolean withinWindow(PromotionRule rule, Instant now) {
        if (rule.getStartDate() != null && now.isBefore(rule.getStartDate())) return false;
        if (rule.getEndDate() != null && !now.isBefore(rule.getEndDate())) return false;
        return true;
    }

    private boolean segmentGateOk(PromotionRule rule, CartContext ctx) {
        if (rule.getTargetSegments() == null || rule.getTargetSegments().isEmpty()) return true;
        if (ctx.userId() == null) return false;
        Set<Long> userSegments = ctx.userSegmentIds() == null
                ? Collections.emptySet() : ctx.userSegmentIds();
        return rule.getTargetSegments().stream()
                .anyMatch(s -> userSegments.contains(s.getId()));
    }

    private List<WorkingLine> linesForRule(PromotionRule rule, List<WorkingLine> allLines) {
        List<WorkingLine> companyLines = allLines.stream()
                .filter(l -> l.companyId() == rule.getCompany().getId())
                .toList();

        boolean hasProductTargets = rule.getTargetProducts() != null && !rule.getTargetProducts().isEmpty();
        boolean hasBundleTargets  = rule.getTargetBundles()  != null && !rule.getTargetBundles().isEmpty();

        if (!hasProductTargets && !hasBundleTargets) {
            return companyLines;
        }

        Set<Long> productIds = hasProductTargets
                ? rule.getTargetProducts().stream().map(Product::getId).collect(Collectors.toSet())
                : Set.of();
        Set<Long> bundleIds = hasBundleTargets
                ? rule.getTargetBundles().stream().map(ProductBundle::getId).collect(Collectors.toSet())
                : Set.of();

        return companyLines.stream()
                .filter(l -> (l.productId() != null && productIds.contains(l.productId()))
                          || (l.bundleId()  != null && bundleIds.contains(l.bundleId())))
                .toList();
    }

    private Coupon resolveCoupon(String code) {
        if (code == null || code.isBlank()) return null;
        return couponRepository.findByCodeIgnoreCase(code.trim()).orElse(null);
    }

    /**
     * Mirrors the existing Coupon validation semantics (status, window, minOrderAmount
     * against post-promotion subtotal, maxUses warning) but does not increment the
     * used-count — the order flow does that atomically at checkout.
     */
    private CouponOutcome applyCoupon(
            Coupon coupon,
            BigDecimal postPromotionSubtotal,
            List<WorkingLine> lines,
            CartContext ctx,
            List<String> warnings) {
        if (coupon.getStatus() != DiscountStatus.ACTIVE) {
            warnings.add("Coupon is not active");
            return CouponOutcome.none();
        }
        Instant now = ctx.now();
        if (coupon.getStartDate() != null && now.isBefore(coupon.getStartDate())) {
            warnings.add("Coupon not yet active");
            return CouponOutcome.none();
        }
        if (coupon.getEndDate() != null && !now.isBefore(coupon.getEndDate())) {
            warnings.add("Coupon has expired");
            return CouponOutcome.none();
        }
        if (coupon.getMaxUses() != null && coupon.getUsedCount() >= coupon.getMaxUses()) {
            warnings.add("Coupon redemption limit reached");
            return CouponOutcome.none();
        }
        if (coupon.getMinOrderAmount() != null
                && postPromotionSubtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            warnings.add("Cart below coupon minOrderAmount");
            return CouponOutcome.none();
        }

        BigDecimal saving;
        if (coupon.getType() == DiscountType.PERCENTAGE) {
            saving = postPromotionSubtotal
                    .multiply(coupon.getValue())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else {
            saving = coupon.getValue().min(postPromotionSubtotal);
        }
        saving = saving.min(postPromotionSubtotal).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        if (saving.signum() <= 0) return CouponOutcome.none();

        // Distribute saving across lines proportional to remaining; last line absorbs rounding.
        BigDecimal pool = lines.stream()
                .map(WorkingLine::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (pool.signum() > 0) {
            BigDecimal taken = BigDecimal.ZERO;
            int lastIdx = lines.size() - 1;
            for (int i = 0; i <= lastIdx; i++) {
                WorkingLine line = lines.get(i);
                BigDecimal share;
                if (i == lastIdx) {
                    share = saving.subtract(taken);
                } else {
                    share = saving.multiply(line.remaining())
                            .divide(pool, 2, RoundingMode.HALF_UP);
                }
                if (share.signum() > 0) {
                    taken = taken.add(line.applySavings(-coupon.getId(), share));
                }
            }
            // if any rounding left uncommitted (cap), use actual taken
            saving = taken.setScale(2, RoundingMode.HALF_UP);
        }
        return new CouponOutcome(saving);
    }

    private PricingResult emptyResult(CartContext ctx) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal shipping = ctx.shippingAmount() != null
                ? ctx.shippingAmount().setScale(2, RoundingMode.HALF_UP)
                : zero;
        return new PricingResult(
                List.of(), List.of(), zero, zero, zero, null, shipping, zero, shipping, List.of());
    }

    private record CouponOutcome(BigDecimal saving) {
        static CouponOutcome none() {
            return new CouponOutcome(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
    }
}
