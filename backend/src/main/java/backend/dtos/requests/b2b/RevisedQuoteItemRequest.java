package backend.dtos.requests.b2b;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** A counter-proposed line: product/variant, quantity, and the vendor's revised unit price (cents). */
public record RevisedQuoteItemRequest(
        @NotNull UUID productId,
        UUID variantId,
        @Min(1) int quantity,
        @Min(0) long unitPriceCents
) {}
