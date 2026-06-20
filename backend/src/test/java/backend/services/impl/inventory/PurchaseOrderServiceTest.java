package backend.services.impl.inventory;

import backend.dtos.requests.inventory.CreatePORequest;
import backend.dtos.requests.inventory.POLineItemRequest;
import backend.dtos.requests.inventory.ReceiveItemsRequest;
import backend.dtos.requests.inventory.ReceiveLineItem;
import backend.dtos.responses.inventory.PurchaseOrderResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.models.core.Company;
import backend.models.core.InventoryAdjustment;
import backend.models.core.Product;
import backend.models.core.PurchaseOrder;
import backend.models.core.PurchaseOrderItem;
import backend.models.core.RestockRequest;
import backend.models.core.Supplier;
import backend.models.core.User;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.CompanyCapability;
import backend.models.enums.POStatus;
import backend.models.enums.RestockStatus;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.InventoryLocationRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.PurchaseOrderItemRepository;
import backend.repositories.PurchaseOrderRepository;
import backend.repositories.RestockRequestRepository;
import backend.repositories.SupplierRepository;
import backend.repositories.UserRepository;
import backend.services.intf.CacheService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.orders.OrderService;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PurchaseOrderServiceTest {

    static final UUID COMPANY_ID  = TestIds.uuid(1);
    static final UUID OWNER_ID    = TestIds.uuid(2);
    static final UUID PRODUCT_ID  = TestIds.uuid(3);
    static final UUID SUPPLIER_ID = TestIds.uuid(4);
    static final UUID PO_ID       = TestIds.uuid(5);
    static final UUID ITEM_ID     = TestIds.uuid(6);
    static final UUID RESTOCK_ID  = TestIds.uuid(7);

    PurchaseOrderRepository poRepository;
    PurchaseOrderItemRepository itemRepository;
    SupplierRepository supplierRepository;
    ProductRepository productRepository;
    ProductVariantRepository variantRepository;
    InventoryLocationRepository locationRepository;
    RestockRequestRepository restockRepository;
    InventoryAdjustmentRepository adjustmentRepository;
    LocationStockRepository locationStockRepository;
    UserRepository userRepository;
    CompanyAccessService companyAccessService;
    CacheService cacheService;
    EmailService emailService;
    StockAlertService stockAlertService;
    OrderService orderService;
    ApplicationEventPublisher eventPublisher;
    PurchaseOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        poRepository            = mock(PurchaseOrderRepository.class);
        itemRepository          = mock(PurchaseOrderItemRepository.class);
        supplierRepository      = mock(SupplierRepository.class);
        productRepository       = mock(ProductRepository.class);
        variantRepository       = mock(ProductVariantRepository.class);
        locationRepository      = mock(InventoryLocationRepository.class);
        restockRepository       = mock(RestockRequestRepository.class);
        adjustmentRepository    = mock(InventoryAdjustmentRepository.class);
        locationStockRepository = mock(LocationStockRepository.class);
        userRepository          = mock(UserRepository.class);
        companyAccessService    = mock(CompanyAccessService.class);
        cacheService            = mock(CacheService.class);
        emailService            = mock(EmailService.class);
        stockAlertService       = mock(StockAlertService.class);
        orderService            = mock(OrderService.class);
        eventPublisher          = mock(ApplicationEventPublisher.class);

        service = new PurchaseOrderServiceImpl(
                poRepository, itemRepository, supplierRepository,
                productRepository, variantRepository, locationRepository,
                restockRepository, adjustmentRepository, locationStockRepository,
                userRepository, companyAccessService, cacheService,
                emailService, stockAlertService, orderService, eventPublisher);

        when(poRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.save(any(PurchaseOrderItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(adjustmentRepository.save(any(InventoryAdjustment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── createPO ─────────────────────────────────────────────────────────────

    @Test
    void shouldCreatePOWithCorrectTotalCostCents() {
        stubCompanyAccess();
        when(supplierRepository.findByIdAndCompanyId(SUPPLIER_ID, COMPANY_ID))
                .thenReturn(Optional.of(supplier()));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(product(10)));
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user());

        CreatePORequest req = new CreatePORequest();
        req.setSupplierId(SUPPLIER_ID);
        req.setItems(List.of(lineItem(PRODUCT_ID, 5, 1000L)));

        PurchaseOrderResponse response = service.createPO(COMPANY_ID, OWNER_ID, req);

        assertThat(response.getStatus()).isEqualTo("DRAFT");
        assertThat(response.getTotalCostCents()).isEqualTo(5000L);
        assertThat(response.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(response.getSupplierId()).isEqualTo(SUPPLIER_ID);
        verify(itemRepository).save(any(PurchaseOrderItem.class));
    }

    // ─── sendPO ───────────────────────────────────────────────────────────────

    @Test
    void shouldTransitionDraftToSentAndSendEmail() {
        stubCompanyAccess();
        PurchaseOrder po = po(POStatus.DRAFT);
        when(poRepository.findByIdAndCompanyId(PO_ID, COMPANY_ID)).thenReturn(Optional.of(po));

        PurchaseOrderItem item = poItem(ITEM_ID, 5, 0);
        when(itemRepository.findAllByPurchaseOrderId(PO_ID)).thenReturn(List.of(item));

        PurchaseOrderResponse response = service.sendPO(COMPANY_ID, PO_ID, OWNER_ID);

        assertThat(response.getStatus()).isEqualTo("SENT");
        assertThat(response.getSentAt()).isNotNull();
        verify(emailService).sendPurchaseOrderEmail(
                eq("supplier@acme.com"), eq("Acme Corp"), eq("ShopWave"),
                any(), any(), isNull());
    }

    @Test
    void shouldThrowBadRequestWhenSendingNonDraftPO() {
        stubCompanyAccess();
        when(poRepository.findByIdAndCompanyId(PO_ID, COMPANY_ID))
                .thenReturn(Optional.of(po(POStatus.SENT)));

        assertThatThrownBy(() -> service.sendPO(COMPANY_ID, PO_ID, OWNER_ID))
                .isInstanceOf(BadRequestException.class)
                .satisfies(t -> assertThat(((BadRequestException) t).getDetail()).contains("DRAFT"));
    }

    @Test
    void shouldThrowBadRequestWhenSendingPOWithSupplierMissingEmail() {
        stubCompanyAccess();
        PurchaseOrder po = po(POStatus.DRAFT);
        po.getSupplier().setEmail(null);
        when(poRepository.findByIdAndCompanyId(PO_ID, COMPANY_ID)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> service.sendPO(COMPANY_ID, PO_ID, OWNER_ID))
                .isInstanceOf(BadRequestException.class)
                .satisfies(t -> assertThat(((BadRequestException) t).getDetail()).contains("no email"));
        verify(emailService, never()).sendPurchaseOrderEmail(any(), any(), any(), any(), any(), any());
        verify(poRepository, never()).save(any());
    }

    // ─── confirmPO ────────────────────────────────────────────────────────────

    @Test
    void shouldThrowBadRequestWhenConfirmingNonSentPO() {
        stubCompanyAccess();
        when(poRepository.findByIdAndCompanyId(PO_ID, COMPANY_ID))
                .thenReturn(Optional.of(po(POStatus.DRAFT)));

        assertThatThrownBy(() -> service.confirmPO(COMPANY_ID, PO_ID, OWNER_ID))
                .isInstanceOf(BadRequestException.class)
                .satisfies(t -> assertThat(((BadRequestException) t).getDetail()).contains("SENT"));
    }

    // ─── receiveItems ─────────────────────────────────────────────────────────

    @Test
    void shouldTransitionToReceivedWhenAllItemsFullyReceived() {
        stubCompanyAccess();
        PurchaseOrder po = po(POStatus.CONFIRMED);
        when(poRepository.findByIdAndCompanyId(PO_ID, COMPANY_ID)).thenReturn(Optional.of(po));

        PurchaseOrderItem item = poItem(ITEM_ID, 5, 0);
        when(itemRepository.findByIdAndPurchaseOrderId(ITEM_ID, PO_ID)).thenReturn(Optional.of(item));
        when(itemRepository.findAllByPurchaseOrderId(PO_ID)).thenAnswer(inv -> List.of(item));
        stubStockAdjustInfra(5);

        PurchaseOrderResponse response = service.receiveItems(COMPANY_ID, PO_ID, OWNER_ID, receiveRequest(ITEM_ID, 5));

        assertThat(response.getStatus()).isEqualTo("RECEIVED");
        assertThat(response.getReceivedAt()).isNotNull();

        ArgumentCaptor<InventoryAdjustment> adjCaptor = ArgumentCaptor.forClass(InventoryAdjustment.class);
        verify(adjustmentRepository).save(adjCaptor.capture());
        InventoryAdjustment adj = adjCaptor.getValue();
        assertThat(adj.getReason()).isEqualTo(AdjustmentReason.PURCHASE_ORDER_RECEIPT);
        assertThat(adj.getDelta()).isEqualTo(5);
        assertThat(adj.getPurchaseOrderId()).isEqualTo(PO_ID);
    }

    @Test
    void shouldTransitionToPartiallyReceivedWhenSomeItemsRemain() {
        stubCompanyAccess();
        PurchaseOrder po = po(POStatus.CONFIRMED);
        when(poRepository.findByIdAndCompanyId(PO_ID, COMPANY_ID)).thenReturn(Optional.of(po));

        PurchaseOrderItem item = poItem(ITEM_ID, 10, 0);
        when(itemRepository.findByIdAndPurchaseOrderId(ITEM_ID, PO_ID)).thenReturn(Optional.of(item));
        when(itemRepository.findAllByPurchaseOrderId(PO_ID)).thenAnswer(inv -> List.of(item));
        stubStockAdjustInfra(3);

        PurchaseOrderResponse response = service.receiveItems(COMPANY_ID, PO_ID, OWNER_ID, receiveRequest(ITEM_ID, 3));

        assertThat(response.getStatus()).isEqualTo("PARTIALLY_RECEIVED");
    }

    @Test
    void shouldCloseLinkedRestockRequestOnFullReceipt() {
        stubCompanyAccess();
        PurchaseOrder po = poWithRestock(POStatus.CONFIRMED, RESTOCK_ID);
        when(poRepository.findByIdAndCompanyId(PO_ID, COMPANY_ID)).thenReturn(Optional.of(po));

        PurchaseOrderItem item = poItem(ITEM_ID, 5, 0);
        when(itemRepository.findByIdAndPurchaseOrderId(ITEM_ID, PO_ID)).thenReturn(Optional.of(item));
        when(itemRepository.findAllByPurchaseOrderId(PO_ID)).thenAnswer(inv -> List.of(item));
        stubStockAdjustInfra(5);

        RestockRequest rr = restockRequest();
        when(restockRepository.findById(RESTOCK_ID)).thenReturn(Optional.of(rr));
        when(restockRepository.save(any(RestockRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.receiveItems(COMPANY_ID, PO_ID, OWNER_ID, receiveRequest(ITEM_ID, 5));

        assertThat(rr.getStatus()).isEqualTo(RestockStatus.RECEIVED);
        verify(restockRepository).save(rr);
    }

    @Test
    void shouldThrowBadRequestWhenReceivingMoreThanOrderedQty() {
        stubCompanyAccess();
        PurchaseOrder po = po(POStatus.CONFIRMED);
        when(poRepository.findByIdAndCompanyId(PO_ID, COMPANY_ID)).thenReturn(Optional.of(po));

        PurchaseOrderItem item = poItem(ITEM_ID, 5, 3);
        when(itemRepository.findByIdAndPurchaseOrderId(ITEM_ID, PO_ID)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.receiveItems(COMPANY_ID, PO_ID, OWNER_ID, receiveRequest(ITEM_ID, 4)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(t -> assertThat(((BadRequestException) t).getDetail()).contains("Cannot receive more than ordered"));
    }

    @Test
    void shouldThrowConflictAndNotAdjustStockWhenConcurrentReceiptRacesAhead() {
        stubCompanyAccess();
        PurchaseOrder po = po(POStatus.CONFIRMED);
        when(poRepository.findByIdAndCompanyId(PO_ID, COMPANY_ID)).thenReturn(Optional.of(po));

        PurchaseOrderItem item = poItem(ITEM_ID, 5, 0);
        when(itemRepository.findByIdAndPurchaseOrderId(ITEM_ID, PO_ID)).thenReturn(Optional.of(item));
        // The atomic guard rejects the increment (a concurrent receipt got there first).
        when(itemRepository.incrementReceivedQty(ITEM_ID, 5)).thenReturn(0);

        assertThatThrownBy(() -> service.receiveItems(COMPANY_ID, PO_ID, OWNER_ID, receiveRequest(ITEM_ID, 5)))
                .isInstanceOf(ConflictException.class);

        // Stock must never be adjusted when the receipt is rejected.
        verify(productRepository, never()).adjustStock(any(UUID.class), anyInt());
        verify(adjustmentRepository, never()).save(any());
    }

    @Test
    void shouldAcquireAndReleaseLockPerItemDuringReceipt() {
        stubCompanyAccess();
        PurchaseOrder po = po(POStatus.CONFIRMED);
        when(poRepository.findByIdAndCompanyId(PO_ID, COMPANY_ID)).thenReturn(Optional.of(po));

        PurchaseOrderItem item = poItem(ITEM_ID, 10, 0);
        when(itemRepository.findByIdAndPurchaseOrderId(ITEM_ID, PO_ID)).thenReturn(Optional.of(item));
        when(itemRepository.findAllByPurchaseOrderId(PO_ID)).thenAnswer(inv -> List.of(item));
        stubStockAdjustInfra(5);

        service.receiveItems(COMPANY_ID, PO_ID, OWNER_ID, receiveRequest(ITEM_ID, 5));

        verify(cacheService, atLeastOnce()).tryLock(startsWith("lock:product:"), any(), eq(10L));
        verify(cacheService, atLeastOnce()).unlock(startsWith("lock:product:"), any());
    }

    // ─── cancelPO ─────────────────────────────────────────────────────────────

    @Test
    void shouldThrowConflictWhenCancellingFullyReceivedPO() {
        stubCompanyAccess();
        when(poRepository.findByIdAndCompanyId(PO_ID, COMPANY_ID))
                .thenReturn(Optional.of(po(POStatus.RECEIVED)));

        assertThatThrownBy(() -> service.cancelPO(COMPANY_ID, PO_ID, OWNER_ID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldReturnCurrentStateWhenCancellingAlreadyCancelledPO() {
        stubCompanyAccess();
        PurchaseOrder po = po(POStatus.CANCELLED);
        when(poRepository.findByIdAndCompanyId(PO_ID, COMPANY_ID)).thenReturn(Optional.of(po));
        when(itemRepository.findAllByPurchaseOrderId(PO_ID)).thenReturn(List.of());

        PurchaseOrderResponse response = service.cancelPO(COMPANY_ID, PO_ID, OWNER_ID);

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        verify(poRepository, never()).save(any());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void stubCompanyAccess() {
        when(companyAccessService.require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_INVENTORY))
                .thenReturn(company());
    }

    private void stubStockAdjustInfra(int delta) {
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(itemRepository.incrementReceivedQty(any(UUID.class), anyInt())).thenReturn(1);
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product(0)))
                .thenReturn(Optional.of(product(delta)));
        when(productRepository.adjustStock(PRODUCT_ID, delta)).thenReturn(1);
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user());
    }

    private Company company() {
        Company c = new Company();
        c.setId(COMPANY_ID);
        c.setName("ShopWave");
        return c;
    }

    private Supplier supplier() {
        Supplier s = new Supplier();
        s.setId(SUPPLIER_ID);
        s.setCompany(company());
        s.setName("Acme Corp");
        s.setEmail("supplier@acme.com");
        return s;
    }

    private User user() {
        User u = new User();
        u.setId(OWNER_ID);
        u.setEmail("owner@test.com");
        return u;
    }

    private Product product(int stock) {
        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setCompany(company());
        p.setName("Widget");
        p.setSku("WGT-1");
        p.setPrice(BigDecimal.TEN);
        p.setCurrency("USD");
        p.setStock(stock);
        p.setLowStockThreshold(2);
        return p;
    }

    private PurchaseOrder po(POStatus status) {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(PO_ID);
        po.setCompany(company());
        po.setSupplier(supplier());
        po.setCreatedBy(user());
        po.setStatus(status);
        return po;
    }

    private PurchaseOrder poWithRestock(POStatus status, UUID restockRequestId) {
        PurchaseOrder po = po(status);
        po.setRestockRequestId(restockRequestId);
        return po;
    }

    private PurchaseOrderItem poItem(UUID id, int orderedQty, int receivedQty) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setPurchaseOrder(po(POStatus.CONFIRMED));
        item.setProduct(product(0));
        item.setOrderedQty(orderedQty);
        item.setReceivedQty(receivedQty);
        item.setUnitCostCents(500L);
        return item;
    }

    private POLineItemRequest lineItem(UUID productId, int qty, long unitCostCents) {
        POLineItemRequest r = new POLineItemRequest();
        r.setProductId(productId);
        r.setOrderedQty(qty);
        r.setUnitCostCents(unitCostCents);
        return r;
    }

    private ReceiveItemsRequest receiveRequest(UUID itemId, int qty) {
        ReceiveLineItem line = new ReceiveLineItem();
        line.setItemId(itemId);
        line.setReceivedQty(qty);
        ReceiveItemsRequest req = new ReceiveItemsRequest();
        req.setItems(List.of(line));
        return req;
    }

    private RestockRequest restockRequest() {
        RestockRequest rr = new RestockRequest();
        rr.setId(RESTOCK_ID);
        rr.setStatus(RestockStatus.PENDING);
        rr.setCompany(company());
        rr.setProduct(product(0));
        rr.setRequestedQty(5);
        return rr;
    }
}
