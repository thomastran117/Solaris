package backend.services.impl.inventory;

import backend.dtos.requests.inventory.SetLocationStockRequest;
import backend.dtos.responses.inventory.LocationStockResponse;
import backend.exceptions.http.ConflictException;
import backend.models.core.Company;
import backend.models.core.InventoryLocation;
import backend.models.core.LocationStock;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.LocationType;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.InventoryLocationRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.UserRepository;
import backend.services.intf.CacheService;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationInventoryServiceImplTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID OWNER_ID = TestIds.uuid(2);
    private static final UUID LOCATION_ID = TestIds.uuid(3);
    private static final UUID PRODUCT_ID = TestIds.uuid(4);
    private static final UUID LOCATION_STOCK_ID = TestIds.uuid(5);

    private InventoryLocationRepository locationRepository;
    private LocationStockRepository locationStockRepository;
    private CompanyAccessService companyAccessService;
    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private InventoryAdjustmentRepository adjustmentRepository;
    private UserRepository userRepository;
    private CacheService cacheService;
    private StockAlertService stockAlertService;
    private LocationInventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        locationRepository = mock(InventoryLocationRepository.class);
        locationStockRepository = mock(LocationStockRepository.class);
        companyAccessService = mock(CompanyAccessService.class);
        productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        adjustmentRepository = mock(InventoryAdjustmentRepository.class);
        userRepository = mock(UserRepository.class);
        cacheService = mock(CacheService.class);
        stockAlertService = mock(StockAlertService.class);

        service = new LocationInventoryServiceImpl(
                locationRepository,
                locationStockRepository,
                companyAccessService,
                productRepository,
                variantRepository,
                adjustmentRepository,
                userRepository,
                cacheService,
                stockAlertService
        );
    }

    @Test
    void createLocation_duplicateCodeThrowsConflict() {
        when(companyAccessService.require(any(), any(), any())).thenReturn(company());
        when(locationRepository.existsByCodeAndCompanyId("TOR-1", COMPANY_ID)).thenReturn(true);

        var request = new backend.dtos.requests.inventory.CreateLocationRequest();
        request.setName("Toronto");
        request.setCode("TOR-1");

        assertThrows(ConflictException.class,
                () -> service.createLocation(COMPANY_ID, OWNER_ID, request));
    }

    @Test
    void deleteLocation_withRemainingStockThrowsConflict() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location()));
        when(locationStockRepository.existsByLocationIdAndStockGreaterThan(LOCATION_ID, 0)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.deleteLocation(COMPANY_ID, LOCATION_ID, OWNER_ID));
    }

    @Test
    void setLocationStock_existingRecordDecreaseSavesAdjustmentAndAlerts() {
        InventoryLocation location = location();
        Product product = product();
        LocationStock existing = locationStock(location, product, 10, 5);

        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(LOCATION_ID, PRODUCT_ID, null))
                .thenReturn(Optional.of(existing));
        when(productRepository.getReferenceById(PRODUCT_ID)).thenReturn(product);
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user());

        SetLocationStockRequest request = new SetLocationStockRequest();
        request.setStock(3);
        request.setLowStockThreshold(5);

        LocationStockResponse response = service.setLocationStock(
                COMPANY_ID, LOCATION_ID, PRODUCT_ID, OWNER_ID, request, null);

        verify(locationStockRepository).setStock(LOCATION_STOCK_ID, 3, 5);
        verify(adjustmentRepository).save(any());
        verify(stockAlertService).checkAndAlertLocation(
                LOCATION_STOCK_ID, "Toronto Warehouse", PRODUCT_ID, "Desk", null, 3, 5);
        verify(cacheService).unlock(any(), any());
        assertEquals(3, response.getStock());
        assertEquals("LOW_STOCK", response.getStockStatus());
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

    private InventoryLocation location() {
        InventoryLocation location = new InventoryLocation();
        location.setId(LOCATION_ID);
        location.setCompany(company());
        location.setName("Toronto Warehouse");
        location.setCode("TOR-1");
        location.setType(LocationType.WAREHOUSE);
        return location;
    }

    private Product product() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("Desk");
        product.setSku("DESK-1");
        product.setPrice(BigDecimal.TEN);
        product.setCurrency("USD");
        return product;
    }

    private LocationStock locationStock(InventoryLocation location, Product product, int stock, Integer threshold) {
        LocationStock ls = new LocationStock();
        ls.setId(LOCATION_STOCK_ID);
        ls.setLocation(location);
        ls.setProduct(product);
        ls.setStock(stock);
        ls.setLowStockThreshold(threshold);
        return ls;
    }
}
