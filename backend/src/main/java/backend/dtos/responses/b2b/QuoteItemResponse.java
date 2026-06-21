package backend.dtos.responses.b2b;

import backend.models.core.B2BQuoteItem;

import java.util.UUID;

public record QuoteItemResponse(
        UUID id,
        UUID productId,
        UUID variantId,
        String productName,
        int quantity,
        long unitPriceCents,
        long totalPriceCents
) {
    public static QuoteItemResponse from(B2BQuoteItem item) {
        return new QuoteItemResponse(
                item.getId(),
                item.getProductId(),
                item.getVariantId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPriceCents(),
                item.getTotalPriceCents()
        );
    }
}
