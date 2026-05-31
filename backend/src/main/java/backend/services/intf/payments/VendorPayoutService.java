package backend.services.intf.payments;

import java.util.UUID;
import backend.dtos.requests.marketplace.VendorAdjustmentRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.vendor.VendorAdjustmentResponse;
import backend.dtos.responses.vendor.VendorBalanceResponse;
import backend.dtos.responses.vendor.VendorPayoutResponse;
import backend.models.enums.PayoutStatus;

public interface VendorPayoutService {

    VendorBalanceResponse getBalance(UUID vendorId, UUID actorUserId);

    PagedResponse<VendorPayoutResponse> listPayouts(UUID vendorId, PayoutStatus status, int page, int size, UUID actorUserId);

    VendorPayoutResponse getPayoutDetail(UUID payoutId, UUID vendorId, UUID actorUserId);

    /** Operator-triggered manual payout for a vendor with available balance. */
    VendorPayoutResponse triggerManualPayout(UUID vendorId, UUID marketplaceId, UUID operatorUserId);

    /** Called by operator to post a manual credit/debit to a vendor's balance. */
    VendorAdjustmentResponse createAdjustment(UUID vendorId, UUID operatorUserId, VendorAdjustmentRequest request);

    // -------------------------------------------------------------------------
    // Webhook callbacks (called from OrderController)
    // -------------------------------------------------------------------------

    /** Called when Stripe confirms a transfer has been paid to the vendor. */
    void handleTransferPaid(String stripeTransferId);

    /** Called when Stripe reports a transfer failure. */
    void handleTransferFailed(String stripeTransferId, String failureReason);
}
