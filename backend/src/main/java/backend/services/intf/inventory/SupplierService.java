package backend.services.intf.inventory;

import backend.dtos.requests.inventory.CreateSupplierRequest;
import backend.dtos.requests.inventory.UpdateSupplierRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.SupplierResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SupplierService {
    PagedResponse<SupplierResponse> listSuppliers(UUID companyId, UUID ownerId, Pageable pageable);
    SupplierResponse createSupplier(UUID companyId, UUID ownerId, CreateSupplierRequest request);
    SupplierResponse updateSupplier(UUID companyId, UUID supplierId, UUID ownerId, UpdateSupplierRequest request);
    void deleteSupplier(UUID companyId, UUID supplierId, UUID ownerId);
}
