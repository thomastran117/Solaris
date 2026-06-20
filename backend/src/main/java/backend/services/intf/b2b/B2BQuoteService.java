package backend.services.intf.b2b;

import backend.dtos.requests.b2b.CreateQuoteRequest;
import backend.dtos.requests.b2b.VendorQuoteResponseRequest;
import backend.dtos.responses.b2b.QuoteResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.models.enums.QuoteStatus;

import java.util.UUID;

/**
 * B2B / wholesale quoting workflow (Feature 12): buyer requests a quote, vendor approves or
 * counter-proposes, buyer accepts (converting to an order) or rejects.
 */
public interface B2BQuoteService {

    /**
     * Buyer requests a quote from a vendor. Snapshots current product/variant prices into the quote
     * lines, auto-creates/links the buyer's {@code B2BAccount}, and notifies the vendor.
     */
    QuoteResponse requestQuote(UUID buyerUserId, UUID vendorCompanyId, CreateQuoteRequest req);

    /**
     * Vendor approves the quote as-is (PENDING_VENDOR → PENDING_BUYER) or counter-proposes revised
     * line pricing. Requires MANAGE_QUOTES on the company. Notifies the buyer.
     */
    QuoteResponse vendorRespondToQuote(UUID companyId, UUID quoteId, UUID ownerId,
                                       VendorQuoteResponseRequest req);

    /**
     * Buyer accepts a PENDING_BUYER quote, creating an order at the negotiated prices. Returns 409 if
     * the quote has expired. IMMEDIATE terms create a Stripe PaymentIntent; net terms create a
     * B2BInvoice (subject to the buyer's approved credit limit).
     */
    OrderResponse buyerAcceptQuote(UUID quoteId, UUID buyerUserId);

    /** Buyer rejects a PENDING_BUYER quote. */
    void buyerRejectQuote(UUID quoteId, UUID buyerUserId);

    QuoteResponse getBuyerQuote(UUID quoteId, UUID buyerUserId);

    PagedResponse<QuoteResponse> listBuyerQuotes(UUID buyerUserId, int page, int size);

    PagedResponse<QuoteResponse> listVendorQuotes(UUID companyId, UUID ownerId, QuoteStatus status,
                                                  int page, int size);

    /** Scheduled: transitions PENDING_BUYER quotes past their expiry to EXPIRED. */
    void expireStaleQuotes();
}
