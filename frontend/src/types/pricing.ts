/** POST /pricing/quote: server-authoritative pricing preview for product and bundle lines. */
export interface PricingQuoteItem {
  productId?: string;
  variantId?: string | null;
  bundleId?: string;
  quantity: number;
}

export interface PricingQuoteRequest {
  items: PricingQuoteItem[];
  couponCode?: string;
  currency?: string;
  shippingAmount?: number;
  /** Without a destination the quote returns zero tax (taxSource NONE). */
  destinationCountry?: string;
  destinationState?: string;
  destinationPostalCode?: string;
}

export interface PricingQuoteLine {
  index: number;
  productId: string | null;
  variantId: string | null;
  bundleId: string | null;
  quantity: number;
  unitBasePrice: number;
  savings: number;
  effectiveLineTotal: number;
  appliedRuleIds: string[];
}

export interface AppliedPromotion {
  ruleId: string;
  name: string;
  ruleType: string;
  savings: number;
}

export type TaxSource = "NONE" | string;

export interface PricingQuote {
  lines: PricingQuoteLine[];
  appliedPromotions: AppliedPromotion[];
  subtotal: number;
  promotionSavings: number;
  couponSavings: number;
  appliedCouponCode: string | null;
  shippingAmount: number;
  shippingSavings: number;
  taxableAmount: number;
  taxRate: number;
  taxAmount: number;
  taxSource: TaxSource;
  finalTotal: number;
  currency: string;
  warnings: string[];
}
