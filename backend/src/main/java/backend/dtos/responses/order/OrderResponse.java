package backend.dtos.responses.order;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class OrderResponse {
    private UUID id;
    private UUID userId;
    private List<OrderItemResponse> items;
    private BigDecimal totalAmount;
    private String currency;
    private String status;
    private String paymentIntentId;
    private String paymentClientSecret;
    private String couponCode;
    private BigDecimal couponDiscountAmount;
    // Fulfillment method
    private String fulfillmentMethod;
    // Pickup fields (PICKUP orders)
    private String pickupLocationName;
    private Instant pickupReadyAt;
    // Scheduled delivery slot (DELIVERY orders)
    // Calendar date (not a zoned instant); pinned to an ISO-8601 string so output never
    // drifts to Jackson's default array form if global date config changes.
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate preferredDeliveryDate;
    private String preferredDeliveryWindow;
    private String deliverySlotStatus;
    // Shipping address (DELIVERY orders)
    private String shipRecipientName;
    private String shipStreet;
    private String shipStreet2;
    private String shipCity;
    private String shipState;
    private String shipPostalCode;
    private String shipCountry;
    private String shipPhoneNumber;
    // Carrier tracking (DELIVERY orders)
    private String trackingNumber;
    private String carrier;
    private Instant shippedAt;
    private Instant deliveredAt;
    private Instant returnedAt;
    private String fulfillmentNote;
    // Refund tracking
    private long refundedAmountCents;
    private UUID assignedDriverId;
    // Shipping rate selection (Feature 13)
    private String shippingRateId;
    private String shippingCarrier;
    private String shippingServiceCode;
    private String shippingServiceName;
    private String shippingRateCurrency;
    private Integer shippingEstimatedDays;
    private long shippingCostCents;
    private Instant shippingRateQuotedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
