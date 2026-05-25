package backend.dtos.responses.order;

import java.time.Instant;

public record TrackingEvent(
        String status,
        String message,
        String location,
        Instant occurredAt
) {}
