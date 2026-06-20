package backend.services.intf.orders;

import java.util.List;
import java.util.UUID;

/**
 * Neutral description of an order to create from an accepted B2B quote (Feature 12), keeping
 * {@code OrderService} decoupled from the B2B entities. Line prices are pre-negotiated, so the
 * promotion engine is bypassed entirely.
 *
 * @param immediate {@code true} for IMMEDIATE terms (Stripe PaymentIntent, order RESERVED);
 *                  {@code false} for net terms (no Stripe, order created PAID).
 */
public record QuoteOrderSpec(
        UUID buyerUserId,
        UUID quoteId,
        String currency,
        boolean immediate,
        List<QuoteOrderLine> lines
) {
    public record QuoteOrderLine(UUID productId, UUID variantId, int quantity, long unitPriceCents) {
        public long totalCents() {
            return unitPriceCents * quantity;
        }
    }

    public long totalCents() {
        return lines.stream().mapToLong(QuoteOrderLine::totalCents).sum();
    }
}
