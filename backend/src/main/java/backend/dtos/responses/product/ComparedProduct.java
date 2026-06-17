package backend.dtos.responses.product;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One column in a product comparison matrix: the core, always-present fields for a single
 * product. Custom key/value specs live in {@link ComparisonRow}s keyed by this product's id.
 *
 * @param rating      average published-review rating, or {@code null} when the product has no reviews
 * @param reviewCount number of published reviews (0 when none); always present
 * @param stockStatus coarse availability label — one of {@code IN_STOCK}, {@code LOW_STOCK},
 *                    {@code OUT_OF_STOCK}
 * @param imageUrl    thumbnail (falling back to the first image), or {@code null} when none
 */
public record ComparedProduct(
        UUID productId,
        String name,
        BigDecimal price,
        String currency,
        Double rating,
        long reviewCount,
        String stockStatus,
        String imageUrl
) {}
