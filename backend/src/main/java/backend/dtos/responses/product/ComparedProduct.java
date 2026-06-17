package backend.dtos.responses.product;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One column in a product comparison matrix: the core, always-present fields for a single
 * product. Custom key/value specs live in {@link ComparisonRow}s keyed by this product's id.
 */
public record ComparedProduct(
        UUID productId,
        String name,
        BigDecimal price,
        String currency,
        Double rating,
        Long reviewCount,
        String stockStatus,
        String imageUrl
) {}
