package backend.services.impl.inventory;

import backend.dtos.requests.inventory.CreateSupplierRequest;
import backend.dtos.requests.inventory.UpdateSupplierRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.SupplierResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.Supplier;
import backend.models.enums.CompanyCapability;
import backend.repositories.PurchaseOrderRepository;
import backend.repositories.SupplierRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplierServiceTest {

    private static final UUID COMPANY_ID  = TestIds.uuid(1);
    private static final UUID OWNER_ID    = TestIds.uuid(2);
    private static final UUID SUPPLIER_ID = TestIds.uuid(3);

    private SupplierRepository supplierRepository;
    private PurchaseOrderRepository purchaseOrderRepository;
    private CompanyAccessService companyAccessService;
    private SupplierServiceImpl service;

    @BeforeEach
    void setUp() {
        supplierRepository = mock(SupplierRepository.class);
        purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        companyAccessService = mock(CompanyAccessService.class);
        service = new SupplierServiceImpl(supplierRepository, purchaseOrderRepository, companyAccessService);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── createSupplier ───────────────────────────────────────────────────────

    @Test
    void shouldCreateSupplierWhenAccessGranted() {
        when(companyAccessService.require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_INVENTORY))
                .thenReturn(company());

        CreateSupplierRequest req = new CreateSupplierRequest();
        req.setName("Acme Corp");
        req.setEmail("supplier@acme.com");
        req.setPhone("+1-555-0000");

        SupplierResponse response = service.createSupplier(COMPANY_ID, OWNER_ID, req);

        assertThat(response.getName()).isEqualTo("Acme Corp");
        assertThat(response.getEmail()).isEqualTo("supplier@acme.com");
        assertThat(response.getCompanyId()).isEqualTo(COMPANY_ID);
        verify(supplierRepository).save(any(Supplier.class));
    }

    @Test
    void shouldThrowForbiddenWhenCreateSupplierWithoutAccess() {
        doThrow(new ForbiddenException("denied"))
                .when(companyAccessService).require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_INVENTORY);

        assertThatThrownBy(() -> service.createSupplier(COMPANY_ID, OWNER_ID, new CreateSupplierRequest()))
                .isInstanceOf(ForbiddenException.class);
        verify(supplierRepository, never()).save(any());
    }

    // ─── listSuppliers ────────────────────────────────────────────────────────

    @Test
    void shouldReturnAllSuppliersForCompany() {
        Pageable pageable = PageRequest.of(0, 50);
        when(companyAccessService.require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_INVENTORY))
                .thenReturn(company());
        when(supplierRepository.findAllByCompanyId(COMPANY_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(supplier("Alpha"), supplier("Beta")), pageable, 2));

        PagedResponse<SupplierResponse> result = service.listSuppliers(COMPANY_ID, OWNER_ID, pageable);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems()).extracting(SupplierResponse::getName).containsExactly("Alpha", "Beta");
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    // ─── updateSupplier ───────────────────────────────────────────────────────

    @Test
    void shouldThrowNotFoundWhenUpdatingNonExistentSupplier() {
        when(companyAccessService.require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_INVENTORY))
                .thenReturn(company());
        when(supplierRepository.findByIdAndCompanyId(SUPPLIER_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSupplier(COMPANY_ID, SUPPLIER_ID, OWNER_ID, new UpdateSupplierRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldOnlyUpdateProvidedFieldsOnPartialUpdate() {
        when(companyAccessService.require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_INVENTORY))
                .thenReturn(company());
        Supplier existing = supplier("Original Name");
        existing.setEmail("original@example.com");
        when(supplierRepository.findByIdAndCompanyId(SUPPLIER_ID, COMPANY_ID))
                .thenReturn(Optional.of(existing));

        UpdateSupplierRequest req = new UpdateSupplierRequest();
        req.setName("Updated Name");

        service.updateSupplier(COMPANY_ID, SUPPLIER_ID, OWNER_ID, req);

        assertThat(existing.getName()).isEqualTo("Updated Name");
        assertThat(existing.getEmail()).isEqualTo("original@example.com");
    }

    // ─── deleteSupplier ───────────────────────────────────────────────────────

    @Test
    void shouldDeleteSupplierWhenFound() {
        when(companyAccessService.require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_INVENTORY))
                .thenReturn(company());
        Supplier existing = supplier("To Delete");
        when(supplierRepository.findByIdAndCompanyId(SUPPLIER_ID, COMPANY_ID))
                .thenReturn(Optional.of(existing));
        when(purchaseOrderRepository.existsBySupplierId(SUPPLIER_ID)).thenReturn(false);

        service.deleteSupplier(COMPANY_ID, SUPPLIER_ID, OWNER_ID);

        verify(supplierRepository).delete(existing);
    }

    @Test
    void shouldThrowConflictWhenDeletingSupplierWithPurchaseOrders() {
        when(companyAccessService.require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_INVENTORY))
                .thenReturn(company());
        Supplier existing = supplier("Has POs");
        when(supplierRepository.findByIdAndCompanyId(SUPPLIER_ID, COMPANY_ID))
                .thenReturn(Optional.of(existing));
        when(purchaseOrderRepository.existsBySupplierId(SUPPLIER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteSupplier(COMPANY_ID, SUPPLIER_ID, OWNER_ID))
                .isInstanceOf(ConflictException.class);
        verify(supplierRepository, never()).delete(any());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Company company() {
        Company c = new Company();
        c.setId(COMPANY_ID);
        c.setName("ShopWave");
        return c;
    }

    private Supplier supplier(String name) {
        Supplier s = new Supplier();
        s.setId(SUPPLIER_ID);
        s.setCompany(company());
        s.setName(name);
        s.setEmail("test@supplier.com");
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        return s;
    }
}
