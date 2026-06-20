package backend.services.impl.inventory;

import backend.dtos.requests.inventory.CreateSupplierRequest;
import backend.dtos.requests.inventory.UpdateSupplierRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.SupplierResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.Supplier;
import backend.models.enums.CompanyCapability;
import backend.repositories.PurchaseOrderRepository;
import backend.repositories.SupplierRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.inventory.SupplierService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final CompanyAccessService companyAccessService;

    public SupplierServiceImpl(SupplierRepository supplierRepository,
                                PurchaseOrderRepository purchaseOrderRepository,
                                CompanyAccessService companyAccessService) {
        this.supplierRepository = supplierRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.companyAccessService = companyAccessService;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<SupplierResponse> listSuppliers(UUID companyId, UUID ownerId, Pageable pageable) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);
        return new PagedResponse<>(
                supplierRepository.findAllByCompanyId(companyId, pageable).map(this::toResponse));
    }

    @Override
    @Transactional
    public SupplierResponse createSupplier(UUID companyId, UUID ownerId, CreateSupplierRequest request) {
        Company company = companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        Supplier supplier = new Supplier();
        supplier.setCompany(company);
        supplier.setName(request.getName());
        supplier.setEmail(request.getEmail());
        supplier.setPhone(request.getPhone());
        supplier.setAddress(request.getAddress());
        supplier.setNotes(request.getNotes());

        return toResponse(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public SupplierResponse updateSupplier(UUID companyId, UUID supplierId, UUID ownerId, UpdateSupplierRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        Supplier supplier = supplierRepository.findByIdAndCompanyId(supplierId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + supplierId));

        if (request.getName() != null) supplier.setName(request.getName());
        if (request.getEmail() != null) supplier.setEmail(request.getEmail());
        if (request.getPhone() != null) supplier.setPhone(request.getPhone());
        if (request.getAddress() != null) supplier.setAddress(request.getAddress());
        if (request.getNotes() != null) supplier.setNotes(request.getNotes());

        return toResponse(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public void deleteSupplier(UUID companyId, UUID supplierId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        Supplier supplier = supplierRepository.findByIdAndCompanyId(supplierId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + supplierId));

        if (purchaseOrderRepository.existsBySupplierId(supplierId)) {
            throw new ConflictException(
                    "Cannot delete supplier with existing purchase orders: " + supplierId);
        }

        supplierRepository.delete(supplier);
    }

    private SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(
                s.getId(),
                s.getCompany().getId(),
                s.getName(),
                s.getEmail(),
                s.getPhone(),
                s.getAddress(),
                s.getNotes(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
