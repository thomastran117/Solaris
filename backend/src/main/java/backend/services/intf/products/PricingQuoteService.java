package backend.services.intf.products;

import java.util.UUID;
import backend.dtos.requests.pricing.PricingQuoteRequest;
import backend.dtos.responses.pricing.PricingQuoteResponse;

/**
 * Thin request/response layer around {@link PricingEngine}. Resolves product/variant prices,
 * loads the caller's segment memberships, invokes the engine, and maps the internal result
 * to the DTO returned by the quote API.
 */
public interface PricingQuoteService {
    PricingQuoteResponse quote(PricingQuoteRequest request, UUID userId);
}
