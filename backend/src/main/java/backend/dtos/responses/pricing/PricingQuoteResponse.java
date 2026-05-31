package backend.dtos.responses.pricing;

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
        BigDecimal finalTotal,
        String currency,
        List<String> warnings
) {}
