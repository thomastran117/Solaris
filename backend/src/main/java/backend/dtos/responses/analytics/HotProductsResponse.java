package backend.dtos.responses.analytics;

import java.time.Instant;
import java.util.List;

/**
 * Response for the hot-products demand-tracking endpoint.
 *
 * window      — the requested time window ("1h" or "24h")
 * computedAt  — when this ranking was calculated (may be from cache)
 * windowStart — the exact Instant that marks the start of the window
 * products    — ordered list of demand entries (rank 1 = highest velocity)
 */
public record HotProductsResponse(
        String window,
        Instant computedAt,
        Instant windowStart,
        List<DemandEntry> products
) {}
