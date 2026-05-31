package backend.dtos.requests.giftcard;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedeemGiftCardRequest(

        @NotBlank(message = "Gift card code is required")
        @Size(max = 20, message = "Gift card code is too long")
        String code,

        @Min(value = 1, message = "Amount must be at least 1 cent")
        long amountCents
) {}
