package backend.services.intf.inventory;

import backend.dtos.requests.inventory.CreatePORequest;
import backend.dtos.requests.inventory.ReceiveItemsRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.PurchaseOrderResponse;
import backend.models.enums.POStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PurchaseOrderService {
    PurchaseOrderResponse createPO(UUID companyId, UUID ownerId, CreatePORequest request);
    PurchaseOrderResponse sendPO(UUID companyId, UUID poId, UUID ownerId);
    PurchaseOrderResponse confirmPO(UUID companyId, UUID poId, UUID ownerId);
    PurchaseOrderResponse receiveItems(UUID companyId, UUID poId, UUID ownerId, ReceiveItemsRequest request);
    PurchaseOrderResponse cancelPO(UUID companyId, UUID poId, UUID ownerId);
    PurchaseOrderResponse getPO(UUID companyId, UUID poId, UUID ownerId);
    PagedResponse<PurchaseOrderResponse> listPOs(UUID companyId, UUID ownerId, POStatus status, Pageable pageable);
}
