package backend.services.impl.inventory;

import backend.dtos.requests.inventory.CreateTransferRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.InventoryTransferResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.exceptions.http.UnprocessableEntityException;
import backend.models.core.Company;
import backend.models.core.InventoryLocation;
import backend.models.core.InventoryTransfer;
import backend.models.core.LocationStock;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.CompanyCapability;
import backend.models.enums.TransferStatus;
import backend.repositories.InventoryLocationRepository;
import backend.repositories.InventoryTransferRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.UserRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.inventory.LocationInventoryService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryTransferServiceImplTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID OWNER_ID = TestIds.uuid(2);
    private static final UUID FROM_ID = TestIds.uuid(3);   // lower than TO_ID -> applied first
    private static final UUID TO_ID = TestIds.uuid(4);
    private static final UUID PRODUCT_ID = TestIds.uuid(5);
    private static final UUID TRANSFER_ID = TestIds.uuid(6);

    private InventoryTransferRepository transferRepository;
    private InventoryLocationRepository locationRepository;
    private LocationStockRepository locationStockRepository;
    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private UserRepository userRepository;
    private CompanyAccessService companyAccessService;
    private LocationInventoryService locationInventoryService;
    private InventoryTransferServiceImpl service;

    @BeforeEach
    void setUp() {
        transferRepository = mock(InventoryTransferRepository.class);
        locationRepository = mock(InventoryLocationRepository.class);
        locationStockRepository = mock(LocationStockRepository.class);
        productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        userRepository = mock(UserRepository.class);
        companyAccessService = mock(CompanyAccessService.class);
        locationInventoryService = mock(LocationInventoryService.class);

        service = new InventoryTransferServiceImpl(
                transferRepository, locationRepository, locationStockRepository,
                productRepository, variantRepository, userRepository,
                companyAccessService, locationInventoryService);
    }

    // ─── createTransfer ───────────────────────────────────────────────────────

    @Test
    void createTransfer_happyPath_persistsPendingTransfer() {
        stubCreateLookups(10);
        when(variantRepository.existsByProductId(PRODUCT_ID)).thenReturn(false);
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user());
        when(transferRepository.save(any(InventoryTransfer.class))).thenAnswer(inv -> {
            InventoryTransfer t = inv.getArgument(0);
            t.setId(TRANSFER_ID);
            return t;
        });

        InventoryTransferResponse resp = service.createTransfer(COMPANY_ID, OWNER_ID, request(5));

        assertEquals(TransferStatus.PENDING, resp.getStatus());
        assertEquals(5, resp.getQuantity());
        verify(companyAccessService).require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_INVENTORY);
        verify(transferRepository).save(any(InventoryTransfer.class));
    }

    @Test
    void createTransfer_insufficientSourceStock_throwsUnprocessableEntity() {
        stubCreateLookups(3);
        when(variantRepository.existsByProductId(PRODUCT_ID)).thenReturn(false);

        assertThrows(UnprocessableEntityException.class,
                () -> service.createTransfer(COMPANY_ID, OWNER_ID, request(5)));
        verify(transferRepository, never()).save(any());
    }

    @Test
    void createTransfer_noSourceStockRecord_throwsUnprocessableEntity() {
        when(companyAccessService.require(any(), any(), any())).thenReturn(company());
        when(locationRepository.findByIdAndCompanyId(FROM_ID, COMPANY_ID)).thenReturn(Optional.of(location(FROM_ID, "Src")));
        when(locationRepository.findByIdAndCompanyId(TO_ID, COMPANY_ID)).thenReturn(Optional.of(location(TO_ID, "Dst")));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product()));
        when(variantRepository.existsByProductId(PRODUCT_ID)).thenReturn(false);
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(FROM_ID, PRODUCT_ID, null))
                .thenReturn(Optional.empty());

        assertThrows(UnprocessableEntityException.class,
                () -> service.createTransfer(COMPANY_ID, OWNER_ID, request(5)));
    }

    // Note: same-location rejection is enforced by Bean Validation (@AssertTrue on
    // CreateTransferRequest) before the service runs; it is covered by InventoryTransferIT
    // (createTransfer_sameLocation_returns400), not at the service layer.

    @Test
    void createTransfer_variantManagedProduct_throwsUnprocessableEntity() {
        when(companyAccessService.require(any(), any(), any())).thenReturn(company());
        when(locationRepository.findByIdAndCompanyId(FROM_ID, COMPANY_ID)).thenReturn(Optional.of(location(FROM_ID, "Src")));
        when(locationRepository.findByIdAndCompanyId(TO_ID, COMPANY_ID)).thenReturn(Optional.of(location(TO_ID, "Dst")));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product()));
        when(variantRepository.existsByProductId(PRODUCT_ID)).thenReturn(true);

        assertThrows(UnprocessableEntityException.class,
                () -> service.createTransfer(COMPANY_ID, OWNER_ID, request(5)));
        verify(transferRepository, never()).save(any());
    }

    @Test
    void createTransfer_productNotFound_throwsResourceNotFound() {
        when(companyAccessService.require(any(), any(), any())).thenReturn(company());
        when(locationRepository.findByIdAndCompanyId(FROM_ID, COMPANY_ID)).thenReturn(Optional.of(location(FROM_ID, "Src")));
        when(locationRepository.findByIdAndCompanyId(TO_ID, COMPANY_ID)).thenReturn(Optional.of(location(TO_ID, "Dst")));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createTransfer(COMPANY_ID, OWNER_ID, request(5)));
    }

    // ─── markInTransit ────────────────────────────────────────────────────────

    @Test
    void markInTransit_happyPath_setsInTransit() {
        InventoryTransfer transfer = transfer(TransferStatus.PENDING, 5);
        when(transferRepository.findByIdAndCompanyId(TRANSFER_ID, COMPANY_ID)).thenReturn(Optional.of(transfer));
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(FROM_ID, PRODUCT_ID, null))
                .thenReturn(Optional.of(locationStock(10)));
        when(transferRepository.save(any(InventoryTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryTransferResponse resp = service.markInTransit(COMPANY_ID, TRANSFER_ID, OWNER_ID);

        assertEquals(TransferStatus.IN_TRANSIT, resp.getStatus());
        assertEquals(TransferStatus.IN_TRANSIT, transfer.getStatus());
    }

    @Test
    void markInTransit_notPending_throwsConflict() {
        InventoryTransfer transfer = transfer(TransferStatus.IN_TRANSIT, 5);
        when(transferRepository.findByIdAndCompanyId(TRANSFER_ID, COMPANY_ID)).thenReturn(Optional.of(transfer));

        assertThrows(ConflictException.class,
                () -> service.markInTransit(COMPANY_ID, TRANSFER_ID, OWNER_ID));
        verify(transferRepository, never()).save(any());
    }

    @Test
    void markInTransit_sourceNowInsufficient_throwsUnprocessableEntity() {
        InventoryTransfer transfer = transfer(TransferStatus.PENDING, 5);
        when(transferRepository.findByIdAndCompanyId(TRANSFER_ID, COMPANY_ID)).thenReturn(Optional.of(transfer));
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(FROM_ID, PRODUCT_ID, null))
                .thenReturn(Optional.of(locationStock(2)));

        assertThrows(UnprocessableEntityException.class,
                () -> service.markInTransit(COMPANY_ID, TRANSFER_ID, OWNER_ID));
    }

    // ─── receiveTransfer ──────────────────────────────────────────────────────

    @Test
    void receiveTransfer_happyPath_movesStockBothLegsAndMarksReceived() {
        InventoryTransfer transfer = transfer(TransferStatus.IN_TRANSIT, 5);
        when(transferRepository.findByIdAndCompanyId(TRANSFER_ID, COMPANY_ID)).thenReturn(Optional.of(transfer));
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user());
        when(transferRepository.saveAndFlush(any(InventoryTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryTransferResponse resp = service.receiveTransfer(COMPANY_ID, TRANSFER_ID, OWNER_ID);

        assertEquals(TransferStatus.RECEIVED, resp.getStatus());
        // The RECEIVED status + @Version bump is flushed BEFORE stock moves (concurrency guard).
        verify(transferRepository).saveAndFlush(any(InventoryTransfer.class));
        // Source decrement (TRANSFER_OUT) and destination increment (TRANSFER_IN).
        verify(locationInventoryService).applyTransferStock(
                eq(COMPANY_ID), eq(FROM_ID), eq(PRODUCT_ID), eq(OWNER_ID), eq(-5),
                eq(AdjustmentReason.TRANSFER_OUT), any());
        verify(locationInventoryService).applyTransferStock(
                eq(COMPANY_ID), eq(TO_ID), eq(PRODUCT_ID), eq(OWNER_ID), eq(5),
                eq(AdjustmentReason.TRANSFER_IN), any());
        verify(locationInventoryService, times(2))
                .applyTransferStock(any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void receiveTransfer_notInTransit_throwsConflict() {
        InventoryTransfer transfer = transfer(TransferStatus.PENDING, 5);
        when(transferRepository.findByIdAndCompanyId(TRANSFER_ID, COMPANY_ID)).thenReturn(Optional.of(transfer));

        assertThrows(ConflictException.class,
                () -> service.receiveTransfer(COMPANY_ID, TRANSFER_ID, OWNER_ID));
        verify(locationInventoryService, never())
                .applyTransferStock(any(), any(), any(), any(), anyInt(), any(), any());
    }

    // ─── cancelTransfer ───────────────────────────────────────────────────────

    @Test
    void cancelTransfer_pending_setsCancelledWithAudit() {
        InventoryTransfer transfer = transfer(TransferStatus.PENDING, 5);
        when(transferRepository.findByIdAndCompanyId(TRANSFER_ID, COMPANY_ID)).thenReturn(Optional.of(transfer));
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user());
        when(transferRepository.save(any(InventoryTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryTransferResponse resp = service.cancelTransfer(COMPANY_ID, TRANSFER_ID, OWNER_ID);

        assertEquals(TransferStatus.CANCELLED, resp.getStatus());
        verify(locationInventoryService, never())
                .applyTransferStock(any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void cancelTransfer_notPending_throwsConflict() {
        InventoryTransfer transfer = transfer(TransferStatus.IN_TRANSIT, 5);
        when(transferRepository.findByIdAndCompanyId(TRANSFER_ID, COMPANY_ID)).thenReturn(Optional.of(transfer));

        assertThrows(ConflictException.class,
                () -> service.cancelTransfer(COMPANY_ID, TRANSFER_ID, OWNER_ID));
        verify(transferRepository, never()).save(any());
    }

    // ─── get / list ───────────────────────────────────────────────────────────

    @Test
    void getTransfer_notFound_throwsResourceNotFound() {
        when(transferRepository.findByIdAndCompanyId(TRANSFER_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getTransfer(COMPANY_ID, TRANSFER_ID, OWNER_ID));
    }

    @Test
    void listTransfers_returnsPagedResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<InventoryTransfer> page = new PageImpl<>(List.of(transfer(TransferStatus.PENDING, 5)), pageable, 1);
        when(transferRepository.findAllByCompanyFiltered(COMPANY_ID, null, null, pageable)).thenReturn(page);

        PagedResponse<InventoryTransferResponse> resp =
                service.listTransfers(COMPANY_ID, OWNER_ID, null, null, pageable);

        assertEquals(1, resp.getItems().size());
        assertEquals(1, resp.getTotalElements());
    }

    // ─── fixtures ─────────────────────────────────────────────────────────────

    private void stubCreateLookups(int sourceStock) {
        when(companyAccessService.require(any(), any(), any())).thenReturn(company());
        when(locationRepository.findByIdAndCompanyId(FROM_ID, COMPANY_ID)).thenReturn(Optional.of(location(FROM_ID, "Src")));
        when(locationRepository.findByIdAndCompanyId(TO_ID, COMPANY_ID)).thenReturn(Optional.of(location(TO_ID, "Dst")));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product()));
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(FROM_ID, PRODUCT_ID, null))
                .thenReturn(Optional.of(locationStock(sourceStock)));
    }

    private CreateTransferRequest request(int qty) {
        CreateTransferRequest req = new CreateTransferRequest();
        req.setProductId(PRODUCT_ID);
        req.setFromLocationId(FROM_ID);
        req.setToLocationId(TO_ID);
        req.setQuantity(qty);
        return req;
    }

    private InventoryTransfer transfer(TransferStatus status, int qty) {
        InventoryTransfer t = new InventoryTransfer();
        t.setId(TRANSFER_ID);
        t.setCompany(company());
        t.setProduct(product());
        t.setFromLocation(location(FROM_ID, "Src"));
        t.setToLocation(location(TO_ID, "Dst"));
        t.setQuantity(qty);
        t.setStatus(status);
        t.setCreatedBy(user());
        return t;
    }

    private Company company() {
        Company c = new Company();
        c.setId(COMPANY_ID);
        c.setName("ShopWave");
        return c;
    }

    private User user() {
        User u = new User();
        u.setId(OWNER_ID);
        u.setEmail("owner@test.com");
        return u;
    }

    private InventoryLocation location(UUID id, String name) {
        InventoryLocation loc = new InventoryLocation();
        loc.setId(id);
        loc.setName(name);
        return loc;
    }

    private Product product() {
        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setName("Desk");
        p.setSku("DESK-1");
        p.setPrice(BigDecimal.TEN);
        p.setCurrency("USD");
        return p;
    }

    private LocationStock locationStock(int stock) {
        LocationStock ls = new LocationStock();
        ls.setId(TestIds.uuid(7));
        ls.setStock(stock);
        return ls;
    }
}
