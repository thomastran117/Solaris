package backend.dtos.responses.product;

import java.math.BigDecimal;
import java.util.UUID;

public record SimilarProductResponse(
        UUID id,
        String name,
        String sku,
        String thumbnailUrl,
        BigDecimal price,
        BigDecimal compareAtPrice,
        String currency,
        String category,
        String brand,
        Double avgRating,
        Long reviewCount,
        String source
) {}
