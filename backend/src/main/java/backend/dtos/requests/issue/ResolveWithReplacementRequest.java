package backend.dtos.requests.issue;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ResolveWithReplacementRequest(
        /** Item variant IDs and quantities to include in the replacement order. */
        @NotEmpty List<ReplacementItem> items,
        @NotBlank @SafeText @Size(max = 255) String shippingAddress,
        @NotBlank @SafeText @Size(max = 100) String shippingCity,
        @NotBlank @SafeText @Size(max = 100) String shippingCountry,
        @NotBlank @SafeText @Size(max = 20) String shippingPostalCode
) {
    public record ReplacementItem(UUID variantId, int quantity) {}
}
