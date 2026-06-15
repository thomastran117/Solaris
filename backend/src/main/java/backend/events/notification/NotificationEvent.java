package backend.events.notification;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalDate;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NotificationEvent.OrderShipped.class,   name = "ORDER_SHIPPED"),
    @JsonSubTypes.Type(value = NotificationEvent.OrderDelivered.class, name = "ORDER_DELIVERED"),
    @JsonSubTypes.Type(value = NotificationEvent.OrderCancelled.class, name = "ORDER_CANCELLED"),
    @JsonSubTypes.Type(value = NotificationEvent.BackInStock.class,    name = "BACK_IN_STOCK"),
    @JsonSubTypes.Type(value = NotificationEvent.DeliverySlotConfirmed.class,   name = "DELIVERY_SLOT_CONFIRMED"),
    @JsonSubTypes.Type(value = NotificationEvent.DeliverySlotUnavailable.class, name = "DELIVERY_SLOT_UNAVAILABLE"),
})
public sealed interface NotificationEvent
        permits NotificationEvent.OrderShipped,
                NotificationEvent.OrderDelivered,
                NotificationEvent.OrderCancelled,
                NotificationEvent.BackInStock,
                NotificationEvent.DeliverySlotConfirmed,
                NotificationEvent.DeliverySlotUnavailable {

    record OrderShipped(
        UUID userId,
        UUID orderId,
        String firstName,
        String trackingNumber,
        String carrier
    ) implements NotificationEvent {}

    record OrderDelivered(
        UUID userId,
        UUID orderId,
        String firstName
    ) implements NotificationEvent {}

    record OrderCancelled(
        UUID userId,
        UUID orderId,
        String firstName,
        String reason
    ) implements NotificationEvent {}

    record BackInStock(
        UUID userId,
        UUID productId,
        String productName,
        UUID variantId,
        String variantTitle,
        String productUrl
    ) implements NotificationEvent {}

    record DeliverySlotConfirmed(
        UUID userId,
        UUID orderId,
        String firstName,
        LocalDate date
    ) implements NotificationEvent {}

    record DeliverySlotUnavailable(
        UUID userId,
        UUID orderId,
        String firstName,
        String reason
    ) implements NotificationEvent {}
}
