package backend.services.intf.orders;

import backend.events.order.DeliveryLocationEvent;

import java.util.UUID;

public interface DeliveryService {

    void processLocationUpdate(UUID orderId, DeliveryLocationEvent event, UUID driverId);

    void processDriverStatus(UUID orderId, String status, UUID driverId);

    void assignDriver(UUID companyId, UUID orderId, UUID driverUserId, UUID ownerId);
}
