package backend.services.intf.checkout;

import backend.events.order.OrderReservationExpiredEvent;

public interface AbandonedCheckoutService {

    void handleExpiredReservation(OrderReservationExpiredEvent event);
}
