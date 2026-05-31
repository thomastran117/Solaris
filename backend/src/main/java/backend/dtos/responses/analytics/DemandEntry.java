package backend.dtos.responses.analytics;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single product entry in a hot-products ranking response.
 *
 * velocityPerHour  — units sold per hour within the requested window.
 *                    For the 1h window this equals unitsSold directly.
 *                    For the 24h window this is unitsSold / 24.
 *
 * accelerationRatio — only meaningful in the 1h response. Compares the
 *                    product's 1h velocity against its own 24h average
 *                    hourly rate. Values > 1.0 indicate the product is
 *                    selling faster than its recent baseline. Always 1.0
 *                    in the 24h response (no shorter-window baseline).
 */
public record DemandEntry(
        UUID productId,
        String productName,
        String sku,
        BigDecimal price,
        String currency,
        long unitsSold,
        BigDecimal revenue,
        double velocityPerHour,
        double accelerationRatio,
        int rank
) {}
