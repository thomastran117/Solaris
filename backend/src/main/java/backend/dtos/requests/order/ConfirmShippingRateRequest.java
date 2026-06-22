package backend.dtos.requests.order;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Body for {@code PATCH /orders/{orderId}/shipping-rate} — the customer's chosen rate id
 * (an EasyPost rate id, or the flat-rate fallback id). Validated server-side against the
 * order's live/cached rates so the cost cannot be spoofed.
 */
@Getter
@Setter
public class ConfirmShippingRateRequest {

    @NotBlank(message = "A shipping rate id is required")
    @Size(max = 100, message = "Rate id must be at most 100 characters")
    @SafeText
    private String rateId;
}
