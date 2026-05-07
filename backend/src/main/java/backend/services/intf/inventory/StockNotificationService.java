package backend.services.intf.inventory;

import backend.dtos.requests.inventory.SubscribeBackInStockRequest;
import backend.dtos.responses.inventory.StockNotificationResponse;

import java.util.List;

public interface StockNotificationService {

    StockNotificationResponse subscribe(long userId, SubscribeBackInStockRequest request);

    void cancel(long userId, long notificationId);

    List<StockNotificationResponse> listByUser(long userId);

    void notifySubscribers(long productId, long variantRef);
}
