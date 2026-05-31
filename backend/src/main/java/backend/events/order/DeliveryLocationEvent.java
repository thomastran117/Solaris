package backend.events.order;

import java.time.Instant;

public record DeliveryLocationEvent(
        double lat,
        double lng,
        Instant timestamp
) {}
