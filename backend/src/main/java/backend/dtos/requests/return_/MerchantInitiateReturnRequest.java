package backend.dtos.requests.return_;

import backend.models.enums.ReturnReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MerchantInitiateReturnRequest(
        @NotEmpty List<@Valid BuyerReturnItemRequest> items,
        ReturnReason reason,
        @backend.annotations.safeText.SafeText @jakarta.validation.constraints.Size(max = 1000) String merchantNote,
        boolean restockItems,
        /**
         * Refund amount in cents.
         * null  = auto-calculate from returned item unit prices × quantities.
         * 0     = intentionally waive the refund.
         * other = merchant-specified amount (supports restocking-fee deductions).
         */
        Long refundAmountOverrideCents,
        /**
         * ID of the CompanyReturnLocation the buyer should ship items to.
         * null = auto-select the primary location, or the first available if no primary is set.
         */
        java.util.UUID returnLocationId
) {}
