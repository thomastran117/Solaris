package backend.dtos.requests.return_;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.Size;

public record MerchantApproveReturnRequest(
        @SafeText @Size(max = 1000) String merchantNote,
        /**
         * Refund amount in cents.
         * null  = auto-calculate from returned item unit prices × quantities.
         * 0     = intentionally waive the refund (no money back).
         * other = merchant-specified amount (supports restocking-fee deductions).
         */
        Long refundAmountOverrideCents,
        /**
         * ID of the CompanyReturnLocation the buyer should ship items to.
         * null = auto-select the primary location, or the first available if no primary is set.
         */
        Long returnLocationId
) {}
