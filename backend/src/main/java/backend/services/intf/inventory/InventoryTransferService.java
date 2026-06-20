package backend.services.intf.inventory;

import backend.dtos.requests.inventory.CreateTransferRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.InventoryTransferResponse;
import backend.models.enums.TransferStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InventoryTransferService {

    InventoryTransferResponse createTransfer(UUID companyId, UUID ownerId, CreateTransferRequest request);

    InventoryTransferResponse getTransfer(UUID companyId, UUID transferId, UUID ownerId);

    PagedResponse<InventoryTransferResponse> listTransfers(UUID companyId, UUID ownerId,
                                                           TransferStatus status, UUID locationId,
                                                           Pageable pageable);

    InventoryTransferResponse markInTransit(UUID companyId, UUID transferId, UUID ownerId);

    InventoryTransferResponse receiveTransfer(UUID companyId, UUID transferId, UUID receivedByUserId);

    InventoryTransferResponse cancelTransfer(UUID companyId, UUID transferId, UUID ownerId);
}
