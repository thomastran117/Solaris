package backend.dtos.responses.order;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
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
    // Fulfillment fields
    private String trackingNumber;
    private String carrier;
    private Instant shippedAt;
    private Instant deliveredAt;
    private Instant returnedAt;
    private String fulfillmentNote;
    // Refund tracking
    private long refundedAmountCents;
    private Instant createdAt;
    private Instant updatedAt;
}
