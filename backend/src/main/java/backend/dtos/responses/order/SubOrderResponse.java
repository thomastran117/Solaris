package backend.dtos.responses.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class SubOrderResponse {
    private UUID id;
    private UUID orderId;
    private UUID marketplaceVendorId;
    private UUID marketplaceId;
    private String vendorCompanyName;
    private String status;
    private BigDecimal subtotal;
    private BigDecimal totalAmount;
    private String currency;
    private BigDecimal commissionAmount;
    private BigDecimal netVendorAmount;
    private String trackingNumber;
    private String carrier;
    private String fulfillmentNote;
    private String cancellationReason;
    private Instant paidAt;
    private Instant packedAt;
    private Instant shippedAt;
    private Instant deliveredAt;
    private Instant cancelledAt;
    private Instant createdAt;
    private Instant updatedAt;
    private List<OrderItemResponse> items;
}
