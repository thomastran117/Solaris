package backend.events.order;

import java.util.UUID;

public record GiftCardIssueRequestedEvent(UUID orderId) {}
