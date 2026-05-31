package backend.dtos.responses.collection;

import backend.dtos.responses.product.ActivePromotionSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Row shape for the admin "products in this collection" page. The product fields are flattened
 * onto the response so the admin UI can render a single table without a separate lookup.
 */
public record CollectionProductResponse(
        UUID id,
        UUID collectionId,
        UUID productId,
        String productName,
        String productSku,
        String productThumbnailUrl,
        BigDecimal productPrice,
        String productCurrency,
        String productStatus,
        Integer boostWeight,
        Integer pinnedRank,
        String source,
        Instant createdAt,
        ActivePromotionSummary activePromotion
) {}
