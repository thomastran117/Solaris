package backend.services.impl.pricing;

import backend.models.core.CommissionPolicy;
import backend.models.core.CommissionRule;
import backend.models.core.MarketplaceVendor;
import backend.models.core.OrderItem;
import backend.models.core.SubOrder;
import backend.repositories.CommissionPolicyRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.OrderItemRepository;
import backend.services.intf.pricing.CommissionEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CommissionEngineImpl implements CommissionEngine {

    private static final Logger log = LoggerFactory.getLogger(CommissionEngineImpl.class);

    private final CommissionPolicyRepository policyRepository;
    private final MarketplaceProfileRepository marketplaceProfileRepository;
    private final OrderItemRepository orderItemRepository;

    public CommissionEngineImpl(
            CommissionPolicyRepository policyRepository,
            MarketplaceProfileRepository marketplaceProfileRepository,
            OrderItemRepository orderItemRepository) {
        this.policyRepository = policyRepository;
        this.marketplaceProfileRepository = marketplaceProfileRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Override
    public CommissionResult compute(SubOrder subOrder) {
        MarketplaceVendor vendor = subOrder.getMarketplaceVendor();
        UUID marketplaceId = vendor.getMarketplace().getId();
        BigDecimal gross = subOrder.getTotalAmount();
        String currency = subOrder.getCurrency();

        CommissionPolicy policy = resolvePolicy(vendor, marketplaceId);

        BigDecimal rate = policy != null
                ? resolveRate(policy, subOrder, vendor)
                : BigDecimal.ZERO;

        BigDecimal commission = gross.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(commission);

        return new CommissionResult(rate, gross, commission, net, currency);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CommissionPolicy resolvePolicy(MarketplaceVendor vendor, UUID marketplaceId) {
        // 1. Vendor-specific override
        if (vendor.getCommissionPolicyId() != null) {
            return policyRepository.findById(vendor.getCommissionPolicyId()).orElse(null);
        }

        // 2. Marketplace default policy ID from MarketplaceProfile
        UUID defaultPolicyId = marketplaceProfileRepository.findByCompanyId(marketplaceId)
                .map(p -> p.getDefaultCommissionPolicyId())
                .orElse(null);

        if (defaultPolicyId != null) {
            return policyRepository.findById(defaultPolicyId).orElse(null);
        }

        // 3. Fall back to any active policy valid now
        List<CommissionPolicy> active = policyRepository.findActiveAt(marketplaceId, Instant.now());
        return active.isEmpty() ? null : active.get(0);
    }

    private BigDecimal resolveRate(CommissionPolicy policy, SubOrder subOrder, MarketplaceVendor vendor) {
        List<OrderItem> items = orderItemRepository.findAllBySubOrderId(subOrder.getId());
        String vendorTier = vendor.getTier().name();

        // Rules are already ordered by priority DESC from the repository
        for (CommissionRule rule : policy.getRules()) {
            if (ruleMatches(rule, items, subOrder, vendorTier)) {
                return rule.getRate();
            }
        }

        return policy.getDefaultRate();
    }

    private boolean ruleMatches(CommissionRule rule, List<OrderItem> items,
                                 SubOrder subOrder, String vendorTier) {
        String matchValue = rule.getMatchValue();
        return switch (rule.getRuleType()) {
            case VENDOR_TIER -> vendorTier.equalsIgnoreCase(matchValue);
            case VOLUME_TIER -> {
                BigDecimal threshold = parseSafe(matchValue);
                yield threshold != null && subOrder.getTotalAmount().compareTo(threshold) >= 0;
            }
            case CATEGORY -> items.stream()
                    .anyMatch(i -> i.getProduct() != null
                            && matchValue.equalsIgnoreCase(i.getProduct().getCategory()));
            case BRAND -> items.stream()
                    .anyMatch(i -> i.getProduct() != null
                            && matchValue.equalsIgnoreCase(i.getProduct().getBrand()));
            case SKU -> items.stream()
                    .anyMatch(i -> i.getVariantSku() != null
                            ? i.getVariantSku().startsWith(matchValue)
                            : i.getProduct() != null && i.getProduct().getSku() != null
                              && i.getProduct().getSku().startsWith(matchValue));
        };
    }

    private BigDecimal parseSafe(String value) {
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            log.warn("[COMMISSION] Cannot parse volume threshold '{}' as decimal", value);
            return null;
        }
    }
}
