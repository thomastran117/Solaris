package backend.services.impl.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.responses.product.ActivePromotionSummary;
import backend.models.core.Product;
import backend.models.core.PromotionRule;
import backend.models.enums.PromotionRuleType;
import backend.repositories.PromotionRuleRepository;
import backend.services.pricing.config.FixedOffConfig;
import backend.services.pricing.config.PercentageOffConfig;
import backend.services.pricing.config.PromotionConfigValidator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds presentation-only {@link ActivePromotionSummary} entries for a collection of products.
 *
 * <p>This is <strong>not</strong> the pricing engine — it never feeds checkout. Its only job is
 * to surface a small, cacheable, per-product hint ("Sale", "-25%", "ends Dec 1") so listing
 * cards and the product page can render strikethrough prices without running the full pricing
 * pipeline once per row. Order/cart pricing remains the responsibility of {@code PricingEngine}.
 *
 * <p>The implementation reuses {@link PromotionRuleRepository#findActiveCandidates} so a single
 * page of products triggers one rule query (grouped by company), then resolves rule→product
 * coverage in memory using the already-loaded {@code targetProducts} association.
 */
@Service
public class ActivePromotionLookupService {

    private static final Logger log = LoggerFactory.getLogger(ActivePromotionLookupService.class);

    private final PromotionRuleRepository ruleRepository;
    private final PromotionConfigValidator configValidator;

    public ActivePromotionLookupService(
            PromotionRuleRepository ruleRepository,
            PromotionConfigValidator configValidator) {
        this.ruleRepository = ruleRepository;
        this.configValidator = configValidator;
    }

    /**
     * Returns a map of {@code productId → ActivePromotionSummary} for every input product that has
     * an in-window, product-applicable rule. Products without a matching rule are absent from the
     * map (callers should treat absence as null).
     *
     * <p>Selection rule: lowest {@code priority} wins; ties broken by largest concrete saving.
     * Bundle-scoped rules ({@code targetBundles} non-empty) are skipped — they aren't shown as
     * product-level discounts.
     */
    @Transactional(readOnly = true)
    public Map<Long, ActivePromotionSummary> findForProducts(Collection<Product> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyMap();
        }

        // Group input product ids by their company so we can issue one rule query per cart of
        // companies. Most catalog pages target a single company, so this is usually one bucket.
        Map<Long, Set<Long>> productIdsByCompany = new HashMap<>();
        for (Product p : products) {
            if (p == null || p.getCompany() == null) continue;
            productIdsByCompany
                    .computeIfAbsent(p.getCompany().getId(), k -> new HashSet<>())
                    .add(p.getId());
        }
        if (productIdsByCompany.isEmpty()) {
            return Collections.emptyMap();
        }

        Instant now = Instant.now();
        List<PromotionRule> candidates = ruleRepository.findActiveCandidates(productIdsByCompany.keySet(), now);
        if (candidates.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, PromotionRule> bestRuleByProduct = new LinkedHashMap<>();
        for (PromotionRule rule : candidates) {
            if (!rule.getTargetBundles().isEmpty()) {
                // Bundle-scoped rules don't surface as per-product discounts.
                continue;
            }
            Long ruleCompanyId = rule.getCompany().getId();
            Set<Long> productsInCompany = productIdsByCompany.get(ruleCompanyId);
            if (productsInCompany == null) continue;

            Set<Long> coveredProductIds = coverageFor(rule, productsInCompany);
            for (Long productId : coveredProductIds) {
                PromotionRule current = bestRuleByProduct.get(productId);
                if (current == null || rulePrecedesCurrent(rule, current)) {
                    bestRuleByProduct.put(productId, rule);
                }
            }
        }

        Map<Long, ActivePromotionSummary> result = new HashMap<>(bestRuleByProduct.size());
        for (Map.Entry<Long, PromotionRule> e : bestRuleByProduct.entrySet()) {
            ActivePromotionSummary summary = toSummary(e.getValue());
            if (summary != null) {
                result.put(e.getKey(), summary);
            }
        }
        return result;
    }

    /**
     * Resolves which of {@code productsInCompany} this rule covers. An empty {@code targetProducts}
     * means the rule applies to the entire company catalogue.
     */
    private Set<Long> coverageFor(PromotionRule rule, Set<Long> productsInCompany) {
        if (rule.getTargetProducts().isEmpty()) {
            return productsInCompany;
        }
        Set<Long> ruleProductIds = new HashSet<>();
        rule.getTargetProducts().forEach(p -> ruleProductIds.add(p.getId()));
        ruleProductIds.retainAll(productsInCompany);
        return ruleProductIds;
    }

    /**
     * Lower priority value wins (matches the pricing engine's sort order). Ties go to the rule
     * with the larger concrete saving so the customer sees the best advertised offer.
     */
    private boolean rulePrecedesCurrent(PromotionRule candidate, PromotionRule incumbent) {
        if (candidate.getPriority() != incumbent.getPriority()) {
            return candidate.getPriority() < incumbent.getPriority();
        }
        BigDecimal a = nominalSaving(candidate);
        BigDecimal b = nominalSaving(incumbent);
        return a.compareTo(b) > 0;
    }

    /**
     * Nominal "headline" saving used only for tie-breaking and UI badge text.
     * Returns BigDecimal.ZERO for non-discount rule types ({@code BOGO}, {@code TIERED_PRICE},
     * {@code FREE_SHIPPING}) — they still show as "Sale" but don't beat a real discount on ties.
     */
    private BigDecimal nominalSaving(PromotionRule rule) {
        try {
            return switch (rule.getRuleType()) {
                case PERCENTAGE_OFF -> {
                    PercentageOffConfig cfg = (PercentageOffConfig)
                            configValidator.parseStored(rule.getRuleType(), rule.getConfigJson());
                    yield cfg.percent() != null ? cfg.percent() : BigDecimal.ZERO;
                }
                case FIXED_OFF -> {
                    FixedOffConfig cfg = (FixedOffConfig)
                            configValidator.parseStored(rule.getRuleType(), rule.getConfigJson());
                    yield cfg.amount() != null ? cfg.amount() : BigDecimal.ZERO;
                }
                default -> BigDecimal.ZERO;
            };
        } catch (Exception e) {
            log.warn("[PROMO LOOKUP] Failed to parse config for rule {}: {}", rule.getId(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private ActivePromotionSummary toSummary(PromotionRule rule) {
        BigDecimal percentOff = null;
        BigDecimal amountOff = null;
        try {
            if (rule.getRuleType() == PromotionRuleType.PERCENTAGE_OFF) {
                PercentageOffConfig cfg = (PercentageOffConfig)
                        configValidator.parseStored(rule.getRuleType(), rule.getConfigJson());
                percentOff = cfg.percent();
            } else if (rule.getRuleType() == PromotionRuleType.FIXED_OFF) {
                FixedOffConfig cfg = (FixedOffConfig)
                        configValidator.parseStored(rule.getRuleType(), rule.getConfigJson());
                amountOff = cfg.amount();
            }
        } catch (Exception e) {
            log.warn("[PROMO LOOKUP] Skipping summary for rule {}: {}", rule.getId(), e.getMessage());
        }
        return new ActivePromotionSummary(
                rule.getId(),
                rule.getRuleType(),
                percentOff,
                amountOff,
                rule.getEndDate()
        );
    }
}
