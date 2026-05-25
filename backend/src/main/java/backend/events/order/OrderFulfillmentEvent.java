package backend.events.order;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OrderFulfillmentEvent.Shipped.class,      name = "SHIPPED"),
        @JsonSubTypes.Type(value = OrderFulfillmentEvent.PickupReady.class,  name = "PICKUP_READY"),
        @JsonSubTypes.Type(value = OrderFulfillmentEvent.Delivered.class,    name = "DELIVERED"),
})
public sealed interface OrderFulfillmentEvent {

    record Shipped(
            UUID orderId,
            UUID userId,
            UUID companyId,
            String trackingNumber,
            String carrier,
            Instant shippedAt
    ) implements OrderFulfillmentEvent {}

    record PickupReady(
            UUID orderId,
            UUID userId,
            UUID companyId,
            String pickupLocationName,
            Instant pickupReadyAt
    ) implements OrderFulfillmentEvent {}

    record Delivered(
            UUID orderId,
            UUID userId,
            UUID companyId,
            Instant deliveredAt
    ) implements OrderFulfillmentEvent {}
}
