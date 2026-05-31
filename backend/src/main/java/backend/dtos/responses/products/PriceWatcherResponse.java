package backend.dtos.responses.products;

import java.time.Instant;
import java.util.UUID;

public record PriceWatcherResponse(
        UUID id,
        UUID productId,
        String productName,
        int watchPriceCents,
        Instant createdAt
) {}
