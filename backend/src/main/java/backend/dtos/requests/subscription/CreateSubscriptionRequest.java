package backend.dtos.requests.subscription;

import backend.models.enums.BillingInterval;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateSubscriptionRequest {

    @NotNull
    private UUID productId;

    private UUID variantId;

    @NotNull
    @Min(1)
    @Max(999)
    private Integer quantity;

    @NotNull
    private BillingInterval billingInterval;

    @NotNull
    @Min(1)
    @Max(12)
    private Integer intervalCount;

    /**
     * Stripe PaymentMethod ID (e.g. "pm_...") collected via a prior SetupIntent.
     * Must belong to the authenticated user's Stripe customer.
     */
    @NotBlank
    @Size(max = 100)
    private String paymentMethodId;

    @NotNull
    @Valid
    private ShippingAddressRequest shippingAddress;

    @Size(min = 3, max = 3)
    private String currency = "USD";
}
