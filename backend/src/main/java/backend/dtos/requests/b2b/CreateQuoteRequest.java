package backend.dtos.requests.b2b;

import backend.annotations.safeRichText.SafeRichText;
import backend.models.enums.PaymentTerms;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Buyer's request for a wholesale quote from a vendor. The business-profile fields
 * ({@code companyName}, {@code taxId}, {@code billingAddress}) auto-create/link the buyer's
 * {@code B2BAccount}. {@code paymentTerms} is the buyer's preference; the vendor can revise it.
 */
public record CreateQuoteRequest(
        @NotBlank @Size(max = 255) String companyName,
        @Size(max = 100) String taxId,
        @Size(max = 500) String billingAddress,
        @Size(max = 2000) @SafeRichText String message,
        @NotNull PaymentTerms paymentTerms,
        @NotEmpty @Valid List<QuoteLineItemRequest> items
) {}
