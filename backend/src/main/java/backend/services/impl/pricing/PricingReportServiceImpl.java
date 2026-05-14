package backend.services.impl.pricing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.responses.pricing.PayoutAttributionResponse;
import backend.dtos.responses.pricing.PromotionRuleAnalyticsResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.CompanyCapability;
import backend.repositories.PromotionRedemptionRepository;
import backend.repositories.PromotionRuleRepository;
import backend.repositories.projections.PayoutAttributionProjection;
import backend.repositories.projections.PromotionRuleAnalyticsProjection;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.pricing.PricingReportService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PricingReportServiceImpl implements PricingReportService {

    private final PromotionRedemptionRepository redemptionRepository;
    private final PromotionRuleRepository ruleRepository;
    private final CompanyAccessService companyAccessService;

    public PricingReportServiceImpl(
            PromotionRedemptionRepository redemptionRepository,
            PromotionRuleRepository ruleRepository,
            CompanyAccessService companyAccessService) {
        this.redemptionRepository = redemptionRepository;
        this.ruleRepository = ruleRepository;
        this.companyAccessService = companyAccessService;
    }

    @Override
    @Transactional(readOnly = true)
    public PayoutAttributionResponse getPayoutAttribution(Instant from, Instant to) {
        validateWindow(from, to);

        List<PayoutAttributionProjection> projections =
                redemptionRepository.aggregatePayoutAttribution(from, to);

        List<PayoutAttributionResponse.Row> rows = new ArrayList<>(projections.size());
        BigDecimal grandTotal = BigDecimal.ZERO;
        long totalRedemptions = 0L;
        for (PayoutAttributionProjection p : projections) {
            BigDecimal savings = p.getTotalSavings() != null ? p.getTotalSavings() : BigDecimal.ZERO;
            long redemptions = p.getRedemptionCount() != null ? p.getRedemptionCount() : 0L;
            long uniqueOrders = p.getUniqueOrderCount() != null ? p.getUniqueOrderCount() : 0L;
            rows.add(new PayoutAttributionResponse.Row(
                    p.getFundedByCompanyId(),
                    p.getCompanyName(),
                    savings,
                    redemptions,
                    uniqueOrders));
            grandTotal = grandTotal.add(savings);
            totalRedemptions += redemptions;
        }
        return new PayoutAttributionResponse(from, to, rows, grandTotal, totalRedemptions);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionRuleAnalyticsResponse getRuleAnalytics(
            long companyId, long ruleId, long ownerId, Instant from, Instant to) {
        validateWindow(from, to);
        companyAccessService.require(companyId, ownerId, CompanyCapability.READ_ANALYTICS);
        ruleRepository.findByIdAndCompanyId(ruleId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion rule not found with id: " + ruleId));

        PromotionRuleAnalyticsProjection p = redemptionRepository.aggregateRuleAnalytics(ruleId, from, to);
        long count = p != null && p.getRedemptionCount() != null ? p.getRedemptionCount() : 0L;
        BigDecimal total = p != null && p.getTotalSavings() != null ? p.getTotalSavings() : BigDecimal.ZERO;
        long orders = p != null && p.getUniqueOrderCount() != null ? p.getUniqueOrderCount() : 0L;
        long users = p != null && p.getUniqueUserCount() != null ? p.getUniqueUserCount() : 0L;
        return new PromotionRuleAnalyticsResponse(ruleId, from, to, count, total, orders, users);
    }

    private void validateWindow(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("from must be ≤ to");
        }
    }
}
