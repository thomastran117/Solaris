package backend.dtos.responses.inventory;

import java.time.Instant;

public record StockNotificationResponse(
    Long id,
    Long productId,
    String productName,
    Long variantId,
    String variantTitle,
    String status,
    Instant createdAt
) {}
