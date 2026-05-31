package backend.events.inventory;

import java.util.UUID;

public record StockRestoredEvent(UUID productId, UUID variantId) {}
