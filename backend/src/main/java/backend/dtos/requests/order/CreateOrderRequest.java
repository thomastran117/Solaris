package backend.dtos.requests.order;

import backend.annotations.safeText.SafeText;
import backend.models.enums.AllocationStrategy;
import backend.models.enums.FulfillmentMethod;
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

    /** DELIVERY (default) ships to the address below; PICKUP collects from pickupLocationId. */
    private FulfillmentMethod fulfillmentMethod = FulfillmentMethod.DELIVERY;

    /** Required when fulfillmentMethod == PICKUP: UUID of a STORE or HYBRID InventoryLocation. */
    private UUID pickupLocationId;

    // Shipping address — required when fulfillmentMethod == DELIVERY.
    // Validated in the service layer because the constraint is conditional on fulfillmentMethod.

    @SafeText @Size(max = 150)
    private String shipRecipientName;

    @SafeText @Size(max = 255)
    private String shipStreet;

    @SafeText @Size(max = 255)
    private String shipStreet2;

    @SafeText @Size(max = 100)
    private String shipCity;

    @SafeText @Size(max = 100)
    private String shipState;

    @Size(max = 20)
    private String shipPostalCode;

    @Pattern(regexp = "^[A-Z]{2}$", message = "country must be a 2-letter ISO 3166-1 alpha-2 code")
    private String shipCountry;

    @Size(max = 30)
    private String shipPhoneNumber;

    @Getter
    @Setter
    public static class OrderItemRequest {

        /** Exactly one of productId, bundleId, or kitId must be set. Validated in service layer. */
        private UUID productId;

        private UUID variantId;

        /** Set to order a bundle instead of a single product. Mutually exclusive with productId/kitId. */
        private UUID bundleId;

        /** Set to order a configurable kit. Mutually exclusive with productId/bundleId. */
        private UUID kitId;

        /** Per-slot component selections for kit orders. Required when kitId is set. */
        @Valid
        private List<KitSelectionRequest> kitSelections;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 999, message = "Quantity must be at most 999")
        private Integer quantity;
    }

    @Getter
    @Setter
    public static class KitSelectionRequest {

        @NotNull
        private UUID slotId;

        private UUID productId;

        private UUID variantId;

        @Min(value = 1)
        @Max(value = 99)
        private int quantity = 1;
    }
}
