package backend.events.inventory;

public record StockRestoredEvent(long productId, Long variantId, long variantRef) {}
