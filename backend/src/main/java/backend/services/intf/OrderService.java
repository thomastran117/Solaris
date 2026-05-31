package backend.services.intf;

import backend.dtos.requests.order.CreateOrderRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.models.enums.OrderStatus;

public interface OrderService {
    OrderResponse createOrder(long userId, CreateOrderRequest request);
    OrderResponse getOrder(long orderId, long userId);
    PagedResponse<OrderResponse> getOrders(long userId, OrderStatus status, int page, int size, String sort, String direction);
    OrderResponse cancelOrder(long orderId, long userId);
    void handlePaymentSuccess(String paymentIntentId);
    void handlePaymentFailure(String paymentIntentId);
}
