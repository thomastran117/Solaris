package backend.services.intf.orders;

import backend.events.order.OrderFulfillmentEvent;

public interface OrderFulfillmentEventPublisher {
    void publish(OrderFulfillmentEvent event);
}
