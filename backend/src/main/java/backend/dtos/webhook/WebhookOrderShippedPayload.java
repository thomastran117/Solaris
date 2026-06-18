package backend.dtos.webhook;

import java.time.Instant;
import java.util.UUID;

/**
 * Public, external-facing webhook payload for {@code ORDER_SHIPPED}. This is a stable contract for
 * webhook subscribers and is intentionally decoupled from the internal
 * {@code OrderFulfillmentEvent.Shipped} record — internal fields (e.g. {@code userId}) are not
 * exposed, and refactoring the internal event does not change what subscribers receive.
 */
public record WebhookOrderShippedPayload(
        UUID orderId,
        UUID companyId,
        String trackingNumber,
        String carrier,
        Instant shippedAt
) {}
