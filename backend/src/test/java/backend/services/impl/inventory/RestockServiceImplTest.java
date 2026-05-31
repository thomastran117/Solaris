package backend.services.impl.inventory;

import backend.dtos.requests.inventory.CreateRestockRequest;
import backend.dtos.requests.inventory.UpdateRestockRequest;
import backend.dtos.responses.inventory.RestockRequestResponse;
import backend.events.inventory.StockRestoredEvent;
import backend.exceptions.http.BadRequestException;
import backend.models.core.Company;
import backend.models.core.InventoryLocation;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.models.core.RestockRequest;
import backend.models.core.User;
import backend.models.enums.RestockStatus;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.InventoryLocationRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.RestockRequestRepository;
import backend.repositories.UserRepository;
import backend.services.intf.CacheService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.orders.OrderService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestockServiceImplTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID OWNER_ID = TestIds.uuid(2);
    private static final UUID PRODUCT_ID = TestIds.uuid(3);
    private static final UUID RESTOCK_ID = TestIds.uuid(4);
    private static final UUID VARIANT_ID = TestIds.uuid(5);
    private static final UUID LOCATION_ID = TestIds.uuid(6);

    private RestockRequestRepository restockRepository;
    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private InventoryLocationRepository locationRepository;
    private LocationStockRepository locationStockRepository;
    private CompanyAccessService companyAccessService;
    private UserRepository userRepository;
    private InventoryAdjustmentRepository adjustmentRepository;
    private CacheService cacheService;
    private StockAlertService stockAlertService;
    private OrderService orderService;
    private ApplicationEventPublisher eventPublisher;
    private RestockServiceImpl service;

    @BeforeEach
    void setUp() {
        restockRepository = mock(RestockRequestRepository.class);
        productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        locationRepository = mock(InventoryLocationRepository.class);
        locationStockRepository = mock(LocationStockRepository.class);
        companyAccessService = mock(CompanyAccessService.class);
        userRepository = mock(UserRepository.class);
        adjustmentRepository = mock(InventoryAdjustmentRepository.class);
        cacheService = mock(CacheService.class);
        stockAlertService = mock(StockAlertService.class);
        orderService = mock(OrderService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        service = new RestockServiceImpl(
                restockRepository,
                productRepository,
                variantRepository,
                locationRepository,
                locationStockRepository,
                companyAccessService,
                userRepository,
                adjustmentRepository,
                cacheService,
                stockAlertService,
                orderService,
                eventPublisher
        );

        when(restockRepository.save(any(RestockRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createRestockRequest_resolvesVariantAndLocation() {
        when(companyAccessService.require(any(), any(), any())).thenReturn(company());
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product(0)));
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant()));
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location()));
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user());
        when(restockRepository.save(any(RestockRequest.class))).thenAnswer(inv -> {
            RestockRequest rr = inv.getArgument(0);
            rr.setId(RESTOCK_ID);
            return rr;
        });

        CreateRestockRequest request = new CreateRestockRequest();
        request.setProductId(PRODUCT_ID);
        request.setVariantId(VARIANT_ID);
        request.setLocationId(LOCATION_ID);
        request.setRequestedQty(12);

        RestockRequestResponse response = service.createRestockRequest(COMPANY_ID, OWNER_ID, request);

        assertEquals(RESTOCK_ID, response.getId());
        assertEquals(VARIANT_ID, response.getVariantId());
        assertEquals(LOCATION_ID, response.getLocationId());
        assertEquals("PENDING", response.getStatus());
    }

    @Test
    void updateRestockRequest_invalidTransitionFromPendingThrows() {
        RestockRequest rr = restockRequest(RestockStatus.PENDING, 10);
        when(restockRepository.findByIdAndCompanyId(RESTOCK_ID, COMPANY_ID)).thenReturn(Optional.of(rr));

        UpdateRestockRequest request = new UpdateRestockRequest();
        request.setStatus(RestockStatus.RECEIVED);
        request.setReceivedQty(5);

        assertThrows(BadRequestException.class,
                () -> service.updateRestockRequest(COMPANY_ID, RESTOCK_ID, OWNER_ID, request));
    }

    @Test
    void updateRestockRequest_receivedTransitionAdjustsStockAndPublishesEvent() {
        RestockRequest rr = restockRequest(RestockStatus.IN_TRANSIT, 5);
        Product before = product(0);
        Product after = product(5);

        when(restockRepository.findByIdAndCompanyId(RESTOCK_ID, COMPANY_ID)).thenReturn(Optional.of(rr));
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(before), Optional.of(after));
        when(productRepository.adjustStock(PRODUCT_ID, 5)).thenReturn(1);
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user());

        UpdateRestockRequest request = new UpdateRestockRequest();
        request.setStatus(RestockStatus.RECEIVED);
        request.setReceivedQty(5);

        RestockRequestResponse response = service.updateRestockRequest(COMPANY_ID, RESTOCK_ID, OWNER_ID, request);

        assertEquals("RECEIVED", response.getStatus());
        assertEquals(5, response.getReceivedQty());
        verify(orderService).fulfillPendingBackorders(PRODUCT_ID, null, 5, null);
        verify(stockAlertService).checkAndAlert(PRODUCT_ID, "Desk", null, null, 5, 2);
        verify(cacheService).unlock(any(), any());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        StockRestoredEvent event = assertInstanceOf(StockRestoredEvent.class, eventCaptor.getValue());
        assertEquals(PRODUCT_ID, event.productId());
        assertEquals(null, event.variantId());
    }

    private Company company() {
        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setOwner(user());
        company.setName("ShopWave");
        return company;
    }

    private User user() {
        User user = new User();
        user.setId(OWNER_ID);
        user.setEmail("owner@test.com");
        return user;
    }

    private Product product(int stock) {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setCompany(company());
        product.setName("Desk");
        product.setSku("DESK-1");
        product.setPrice(BigDecimal.TEN);
        product.setCurrency("USD");
        product.setStock(stock);
        product.setLowStockThreshold(2);
        return product;
    }

    private ProductVariant variant() {
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT_ID);
        variant.setProduct(product(0));
        variant.setSku("DESK-BLACK");
        return variant;
    }

    private InventoryLocation location() {
        InventoryLocation location = new InventoryLocation();
        location.setId(LOCATION_ID);
        location.setCompany(company());
        location.setName("Toronto");
        return location;
    }

    private RestockRequest restockRequest(RestockStatus status, int requestedQty) {
        RestockRequest rr = new RestockRequest();
        rr.setId(RESTOCK_ID);
        rr.setCompany(company());
        rr.setProduct(product(0));
        rr.setCreatedBy(user());
        rr.setStatus(status);
        rr.setRequestedQty(requestedQty);
        rr.setExpectedArrivalDate(LocalDate.of(2026, 5, 25));
        return rr;
    }
}
