package backend.services.intf.orders;

import backend.dtos.responses.order.TrackingEvent;

import java.util.List;
import java.util.UUID;

public interface TrackingService {
    /** Register a shipment with Aftership. Never throws — tracking failures must not block the order flow. */
    void registerTracking(UUID orderId, String trackingNumber, String carrier);

    /** Fetch current tracking events proxied from Aftership. Returns empty list on any failure. */
    List<TrackingEvent> getTrackingEvents(String trackingNumber, String carrier);
}
