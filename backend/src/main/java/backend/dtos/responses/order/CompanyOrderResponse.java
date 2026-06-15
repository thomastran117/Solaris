package backend.dtos.responses.order;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CompanyOrderResponse(
        UUID orderId,
        UUID buyerUserId,
        String orderStatus,
        String currency,
        BigDecimal companyItemsTotal,
        List<OrderItemResponse> items,
        // Fulfillment method
        String fulfillmentMethod,
        // Pickup fields
        UUID pickupLocationId,
        String pickupLocationName,
        Instant pickupReadyAt,
        // Scheduled delivery slot
        LocalDate preferredDeliveryDate,
        String preferredDeliveryWindow,
        String deliverySlotStatus,
        // Shipping address
        String shipRecipientName,
        String shipStreet,
        String shipStreet2,
        String shipCity,
        String shipState,
        String shipPostalCode,
        String shipCountry,
        String shipPhoneNumber,
        // Carrier tracking
        String trackingNumber,
        String carrier,
        Instant shippedAt,
        Instant deliveredAt,
        Instant returnedAt,
        String fulfillmentNote,
        long refundedAmountCents,
        UUID assignedDriverId,
        Instant createdAt
) {}
