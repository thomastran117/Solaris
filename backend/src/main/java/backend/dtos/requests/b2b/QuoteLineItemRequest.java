package backend.dtos.requests.b2b;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** A requested line on a quote: which product/variant and how many. */
public record QuoteLineItemRequest(
        @NotNull UUID productId,
        UUID variantId,
        @Min(1) int quantity
) {}
