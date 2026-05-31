package backend.events;

import java.util.UUID;

public record ProductRemoveEvent(UUID productId, UUID marketplaceId) {}
