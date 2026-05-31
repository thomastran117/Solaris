package backend.dtos.responses.inventory;

import java.time.Instant;
import java.util.UUID;

public record StockNotificationResponse(
    UUID id,
    UUID productId,
    String productName,
    UUID variantId,
    String variantTitle,
    String status,
    Instant createdAt
) {}
