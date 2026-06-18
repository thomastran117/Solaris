package backend.dtos.webhook;

import java.time.Instant;
import java.util.UUID;

/**
 * Public, external-facing webhook payload for {@code ORDER_DELIVERED}. Stable subscriber contract,
 * decoupled from the internal {@code OrderFulfillmentEvent.Delivered} record (see
 * {@link WebhookOrderShippedPayload}).
 */
public record WebhookOrderDeliveredPayload(
        UUID orderId,
        UUID companyId,
        Instant deliveredAt
) {}
