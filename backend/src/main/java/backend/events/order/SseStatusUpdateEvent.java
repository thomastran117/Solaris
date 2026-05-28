package backend.events.order;

import java.time.Instant;
import java.util.UUID;

public record SseStatusUpdateEvent(
        UUID eventId,
        UUID orderId,
        String status,
        String trackingNumber,
        String carrier,
        Double driverLat,
        Double driverLng,
        String note,
        Instant occurredAt,
        String eventType
) {}
