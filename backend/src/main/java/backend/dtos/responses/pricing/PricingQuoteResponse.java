package backend.dtos.responses.pricing;

import backend.models.enums.TaxSource;

import java.math.BigDecimal;
import java.util.List;

public record PricingQuoteResponse(
        List<LineBreakdownResponse> lines,
        List<AppliedPromotionResponse> appliedPromotions,
        BigDecimal subtotal,
        BigDecimal promotionSavings,
        BigDecimal couponSavings,
        String appliedCouponCode,
        BigDecimal shippingAmount,
        BigDecimal shippingSavings,
        BigDecimal taxableAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        TaxSource taxSource,
        BigDecimal finalTotal,
        String currency,
        List<String> warnings
) {}
