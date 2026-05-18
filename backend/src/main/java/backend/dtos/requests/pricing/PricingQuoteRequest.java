package backend.dtos.requests.pricing;

import backend.annotations.safeIdentifier.SafeIdentifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Stateless quote request. Server-authoritative pricing: the caller sends only
 * (productId, variantId?, quantity) per product line or (bundleId, quantity) per bundle line.
 * Exactly one of productId / bundleId must be set on each item.
 */
@Getter
@Setter
@NoArgsConstructor
public class PricingQuoteRequest {

    @NotEmpty(message = "items must contain at least one line")
    @Valid
    private List<Item> items;

    @SafeIdentifier
    @Size(max = 100)
    private String couponCode;

    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO 4217 code")
    private String currency;
    private BigDecimal shippingAmount;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        /** Present for product lines; null for bundle lines. */
        private UUID productId;

        /** Optional — when set, overrides product-level price. Only valid when productId is set. */
        private UUID variantId;

        /** Present for bundle lines; null for product lines. */
        private UUID bundleId;

        @Min(value = 1, message = "quantity must be >= 1")
        private int quantity;

        @AssertTrue(message = "exactly one of productId or bundleId must be provided")
        public boolean isLineTypeValid() {
            return (productId != null) != (bundleId != null);
        }
    }
}
