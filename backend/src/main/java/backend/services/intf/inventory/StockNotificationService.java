package backend.services.intf.inventory;

import java.util.UUID;
import backend.dtos.requests.inventory.SubscribeBackInStockRequest;
import backend.dtos.responses.inventory.StockNotificationResponse;

import java.util.List;

public interface StockNotificationService {

    StockNotificationResponse subscribe(UUID userId, SubscribeBackInStockRequest request);

    void cancel(UUID userId, UUID notificationId);

    List<StockNotificationResponse> listByUser(UUID userId);

    void notifySubscribers(UUID productId, UUID variantId);
}
