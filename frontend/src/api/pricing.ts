import api from "../api";
import type { PricingQuote, PricingQuoteRequest } from "../types/pricing";

export const pricingApi = {
  /** Stateless price preview; the order itself is re-priced on creation. */
  quote: (body: PricingQuoteRequest) =>
    api.post<PricingQuote>("/pricing/quote", body),
};
