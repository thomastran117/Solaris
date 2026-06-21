package backend.dtos.requests.b2b;

import backend.annotations.safeRichText.SafeRichText;
import backend.models.enums.PaymentTerms;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Vendor's response to a pending quote. {@code APPROVE} accepts the quote as-is (moves it to
 * PENDING_BUYER); {@code COUNTER} replaces the line items with revised pricing. {@code paymentTerms}
 * may set/override the terms offered to the buyer.
 */
public record VendorQuoteResponseRequest(
        @NotNull VendorQuoteAction action,
        @Size(max = 2000) @SafeRichText String vendorNote,
        PaymentTerms paymentTerms,
        @Valid List<RevisedQuoteItemRequest> items
) {
    public enum VendorQuoteAction { APPROVE, COUNTER }

    @AssertTrue(message = "items are required (and must be non-empty) when action is COUNTER")
    public boolean isItemsProvidedForCounter() {
        return action != VendorQuoteAction.COUNTER || (items != null && !items.isEmpty());
    }
}
