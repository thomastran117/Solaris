package backend.controllers.impl.orders;

import backend.events.order.DeliveryLocationEvent;
import backend.events.order.DeliveryStatusMessage;
import backend.services.intf.orders.DeliveryService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
public class DeliveryMessageController {

    private final DeliveryService deliveryService;

    public DeliveryMessageController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @MessageMapping("/delivery/{orderId}/location")
    public void handleLocationUpdate(@DestinationVariable UUID orderId,
                                     @Payload DeliveryLocationEvent event,
                                     Principal principal) {
        deliveryService.processLocationUpdate(orderId, event, resolveDriverId(principal));
    }

    @MessageMapping("/delivery/{orderId}/status")
    public void handleStatusUpdate(@DestinationVariable UUID orderId,
                                   @Payload DeliveryStatusMessage msg,
                                   Principal principal) {
        deliveryService.processDriverStatus(orderId, msg.status(), resolveDriverId(principal));
    }

    private UUID resolveDriverId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
