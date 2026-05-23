package backend.dtos.requests.order;

import backend.models.enums.AllocationStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreateOrderRequest {

    @NotEmpty(message = "Order must contain at least one item")
    @Size(max = 200, message = "Order cannot contain more than 200 items")
    @Valid
    private List<OrderItemRequest> items;

    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency = "USD";

    /** Optional coupon code to apply at checkout. Validated and applied in the order service. */
    @Size(max = 50)
    private String couponCode;

    /** Buyer latitude for NEAREST allocation strategy. Range [-90, 90]. */
    @DecimalMin(value = "-90.0", message = "Buyer latitude must be >= -90")
    @DecimalMax(value = "90.0",  message = "Buyer latitude must be <= 90")
    private Double buyerLatitude;

    /** Buyer longitude for NEAREST allocation strategy. Range [-180, 180]. */
    @DecimalMin(value = "-180.0", message = "Buyer longitude must be >= -180")
    @DecimalMax(value = "180.0",  message = "Buyer longitude must be <= 180")
    private Double buyerLongitude;

    /** Allocation strategy. Defaults to HIGHEST_STOCK when absent.
     *  NEAREST falls back to HIGHEST_STOCK if buyer coords are absent.
     *  CHEAPEST falls back to HIGHEST_STOCK if no location has fulfillmentCost set. */
    private AllocationStrategy allocationStrategy;

    /** Step-up token supplied on retry after a risk VERIFY response. Consumed atomically server-side. */
    @Size(max = 64)
    private String riskVerificationToken;

    /** Loyalty points to redeem for a discount on this order. Null or 0 = don't use points. */
    @Min(value = 0, message = "Loyalty points to redeem cannot be negative")
    private Integer loyaltyPointsToRedeem;

    @Getter
    @Setter
    public static class OrderItemRequest {

        /** Either productId or bundleId must be set, not both. Validated in service layer. */
        private UUID productId;

        private UUID variantId;

        /** Set to order a bundle instead of a single product. Mutually exclusive with productId. */
        private UUID bundleId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 999, message = "Quantity must be at most 999")
        private Integer quantity;
    }
}
