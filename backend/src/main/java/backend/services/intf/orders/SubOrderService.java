package backend.services.intf.orders;

import java.util.UUID;
import backend.dtos.requests.order.CancelSubOrderRequest;
import backend.dtos.requests.order.ShipSubOrderRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.CommissionRecordResponse;
import backend.dtos.responses.order.SubOrderResponse;
import backend.models.enums.SubOrderStatus;

public interface SubOrderService {

    /** Returns paginated sub-orders for a vendor, optionally filtered by status. */
    PagedResponse<SubOrderResponse> listVendorSubOrders(UUID marketplaceVendorId, SubOrderStatus status, int page, int size, UUID ownerId);

    SubOrderResponse getSubOrder(UUID subOrderId, UUID marketplaceVendorId, UUID ownerId);

    SubOrderResponse markPacked(UUID subOrderId, UUID marketplaceVendorId, UUID ownerId);

    SubOrderResponse markShipped(UUID subOrderId, UUID marketplaceVendorId, ShipSubOrderRequest request, UUID ownerId);

    SubOrderResponse markDelivered(UUID subOrderId, UUID marketplaceVendorId, UUID ownerId);

    SubOrderResponse cancelSubOrder(UUID subOrderId, UUID marketplaceVendorId, CancelSubOrderRequest request, UUID ownerId);

    CommissionRecordResponse getCommissionRecord(UUID subOrderId, UUID marketplaceVendorId, UUID ownerId);
}
