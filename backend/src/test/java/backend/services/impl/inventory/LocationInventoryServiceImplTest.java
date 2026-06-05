package backend.services.impl.inventory;

import backend.dtos.requests.inventory.AdjustStockRequest;
import backend.dtos.requests.inventory.CreateLocationRequest;
import backend.dtos.requests.inventory.SetLocationStockRequest;
import backend.dtos.requests.inventory.UpdateLocationRequest;
import backend.dtos.responses.inventory.LocationResponse;
import backend.dtos.responses.inventory.LocationStockResponse;
import backend.exceptions.http.ConflictException;
import backend.models.core.Company;
import backend.models.core.InventoryLocation;
import backend.models.core.LocationStock;
import backend.models.core.Product;
import backend.models.core.User;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.CompanyCapability;
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

import backend.models.core.ProductVariant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

    // ─── getLocations ──────────────────────────────────────────────────────────

    @Test
    void getLocations_returnsAllForCompany() {
        when(locationRepository.findAllByCompanyIdOrderByDisplayOrderAscNameAsc(COMPANY_ID))
                .thenReturn(java.util.List.of(location()));

        java.util.List<LocationResponse> result = service.getLocations(COMPANY_ID, OWNER_ID);

        assertEquals(1, result.size());
        verify(companyAccessService).require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_INVENTORY);
    }

    // ─── getLocation ──────────────────────────────────────────────────────────

    @Test
    void getLocation_found_returnsResponse() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID))
                .thenReturn(Optional.of(location()));

        LocationResponse resp = service.getLocation(COMPANY_ID, LOCATION_ID, OWNER_ID);

        assertNotNull(resp);
    }

    @Test
    void getLocation_notFound_throwsResourceNotFoundException() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.getLocation(COMPANY_ID, LOCATION_ID, OWNER_ID));
    }

    // ─── createLocation happy path ────────────────────────────────────────────

    @Test
    void createLocation_happyPath_savesAndReturns() {
        when(companyAccessService.require(any(), any(), any())).thenReturn(company());
        when(locationRepository.existsByCodeAndCompanyId("WH02", COMPANY_ID)).thenReturn(false);
        when(locationRepository.save(any(InventoryLocation.class))).thenAnswer(inv -> {
            InventoryLocation loc = inv.getArgument(0);
            loc.setId(TestIds.uuid(99));
            return loc;
        });

        CreateLocationRequest req = new CreateLocationRequest();
        req.setName("Vancouver");
        req.setCode("WH02");
        req.setType(LocationType.WAREHOUSE);

        LocationResponse resp = service.createLocation(COMPANY_ID, OWNER_ID, req);

        assertNotNull(resp);
        verify(locationRepository).save(any(InventoryLocation.class));
    }

    @Test
    void createLocation_storeWithPickupHours_allowed() {
        when(companyAccessService.require(any(), any(), any())).thenReturn(company());
        when(locationRepository.existsByCodeAndCompanyId("ST01", COMPANY_ID)).thenReturn(false);
        when(locationRepository.save(any(InventoryLocation.class))).thenAnswer(inv -> {
            InventoryLocation loc = inv.getArgument(0);
            loc.setId(TestIds.uuid(99));
            return loc;
        });

        CreateLocationRequest req = new CreateLocationRequest();
        req.setName("Storefront");
        req.setCode("ST01");
        req.setType(LocationType.STORE);
        req.setPickupReadyHours(4);

        assertNotNull(service.createLocation(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createLocation_warehouseWithPickupHours_throwsBadRequest() {
        when(companyAccessService.require(any(), any(), any())).thenReturn(company());
        when(locationRepository.existsByCodeAndCompanyId("WH03", COMPANY_ID)).thenReturn(false);

        CreateLocationRequest req = new CreateLocationRequest();
        req.setName("Bad");
        req.setCode("WH03");
        req.setType(LocationType.WAREHOUSE);
        req.setPickupReadyHours(24); // not allowed for WAREHOUSE

        assertThrows(BadRequestException.class, () -> service.createLocation(COMPANY_ID, OWNER_ID, req));
    }

    // ─── updateLocation ────────────────────────────────────────────────────────

    @Test
    void updateLocation_notFound_throwsResourceNotFoundException() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.updateLocation(COMPANY_ID, LOCATION_ID, OWNER_ID, new UpdateLocationRequest()));
    }

    @Test
    void updateLocation_codeConflict_throwsConflict() {
        InventoryLocation existing = location();
        existing.setCode("TOR-1");
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(locationRepository.existsByCodeAndCompanyIdAndIdNot("VAN-1", COMPANY_ID, LOCATION_ID)).thenReturn(true);

        UpdateLocationRequest req = new UpdateLocationRequest();
        req.setCode("VAN-1");

        assertThrows(ConflictException.class, () ->
                service.updateLocation(COMPANY_ID, LOCATION_ID, OWNER_ID, req));
    }

    @Test
    void updateLocation_happyPath_updatesFields() {
        InventoryLocation existing = location();
        existing.setCode("TOR-1");
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(locationRepository.save(any(InventoryLocation.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateLocationRequest req = new UpdateLocationRequest();
        req.setName("Updated Name");

        service.updateLocation(COMPANY_ID, LOCATION_ID, OWNER_ID, req);

        assertEquals("Updated Name", existing.getName());
        verify(locationRepository).save(existing);
    }

    // ─── deleteLocation ────────────────────────────────────────────────────────

    @Test
    void deleteLocation_noStock_deletesLocation() {
        InventoryLocation loc = location();
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(loc));
        when(locationStockRepository.existsByLocationIdAndStockGreaterThan(LOCATION_ID, 0)).thenReturn(false);

        service.deleteLocation(COMPANY_ID, LOCATION_ID, OWNER_ID);

        verify(locationRepository).delete(loc);
    }

    @Test
    void deleteLocation_locationNotFound_throwsResourceNotFoundException() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.deleteLocation(COMPANY_ID, LOCATION_ID, OWNER_ID));
    }

    // ─── getLocationStock ─────────────────────────────────────────────────────

    @Test
    void getLocationStock_locationNotFound_throwsResourceNotFoundException() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.getLocationStock(COMPANY_ID, LOCATION_ID, OWNER_ID));
    }

    @Test
    void getLocationStock_returnsStockEntries() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location()));
        LocationStock ls = locationStock(location(), product(), 8, null);
        when(locationStockRepository.findAllByLocationId(LOCATION_ID)).thenReturn(java.util.List.of(ls));

        java.util.List<LocationStockResponse> result = service.getLocationStock(COMPANY_ID, LOCATION_ID, OWNER_ID);

        assertEquals(1, result.size());
    }

    // ─── setLocationStock — lock not acquired ─────────────────────────────────

    @Test
    void setLocationStock_lockNotAcquired_throwsConflict() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location()));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product()));
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(false);

        SetLocationStockRequest req = new SetLocationStockRequest();
        req.setStock(5);

        assertThrows(ConflictException.class, () ->
                service.setLocationStock(COMPANY_ID, LOCATION_ID, PRODUCT_ID, OWNER_ID, req, null));
    }

    @Test
    void setLocationStock_newRecord_createsLocationStock() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location()));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product()));
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(LOCATION_ID, PRODUCT_ID, null))
                .thenReturn(Optional.empty());
        when(productRepository.getReferenceById(PRODUCT_ID)).thenReturn(product());
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user());
        when(locationStockRepository.save(any(LocationStock.class))).thenAnswer(inv -> {
            LocationStock ls = inv.getArgument(0);
            ls.setId(TestIds.uuid(60));
            return ls;
        });

        SetLocationStockRequest req = new SetLocationStockRequest();
        req.setStock(20);

        LocationStockResponse resp = service.setLocationStock(
                COMPANY_ID, LOCATION_ID, PRODUCT_ID, OWNER_ID, req, null);

        assertNotNull(resp);
        verify(locationStockRepository).save(any(LocationStock.class));
        verify(adjustmentRepository).save(any());
    }

    // ─── adjustLocationStock ──────────────────────────────────────────────────

    @Test
    void adjustLocationStock_stockRecordNotFound_throwsResourceNotFoundException() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location()));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product()));
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(LOCATION_ID, PRODUCT_ID, null))
                .thenReturn(Optional.empty());

        AdjustStockRequest req = new AdjustStockRequest();
        req.setDelta(5);
        req.setReason(AdjustmentReason.MANUAL_ADJUSTMENT);

        assertThrows(ResourceNotFoundException.class, () ->
                service.adjustLocationStock(COMPANY_ID, LOCATION_ID, PRODUCT_ID, OWNER_ID, req, null));
    }

    @Test
    void adjustLocationStock_lockNotAcquired_throwsConflict() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location()));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product()));
        LocationStock ls = locationStock(location(), product(), 10, null);
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(LOCATION_ID, PRODUCT_ID, null))
                .thenReturn(Optional.of(ls));
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(false);

        AdjustStockRequest req = new AdjustStockRequest();
        req.setDelta(2);
        req.setReason(AdjustmentReason.MANUAL_ADJUSTMENT);

        assertThrows(ConflictException.class, () ->
                service.adjustLocationStock(COMPANY_ID, LOCATION_ID, PRODUCT_ID, OWNER_ID, req, null));
    }

    @Test
    void adjustLocationStock_negativeResult_throwsBadRequest() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location()));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product()));
        LocationStock ls = locationStock(location(), product(), 5, null);
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(LOCATION_ID, PRODUCT_ID, null))
                .thenReturn(Optional.of(ls));
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(locationStockRepository.adjustStock(eq(LOCATION_STOCK_ID), anyInt())).thenReturn(0);

        AdjustStockRequest req = new AdjustStockRequest();
        req.setDelta(-10);
        req.setReason(AdjustmentReason.MANUAL_ADJUSTMENT);

        assertThrows(BadRequestException.class, () ->
                service.adjustLocationStock(COMPANY_ID, LOCATION_ID, PRODUCT_ID, OWNER_ID, req, null));
    }

    @Test
    void adjustLocationStock_happyPath_recordsAdjustment() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location()));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product()));
        LocationStock ls = locationStock(location(), product(), 10, null);
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(LOCATION_ID, PRODUCT_ID, null))
                .thenReturn(Optional.of(ls));
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(locationStockRepository.adjustStock(eq(LOCATION_STOCK_ID), eq(5))).thenReturn(1);
        when(productRepository.getReferenceById(PRODUCT_ID)).thenReturn(product());
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user());

        AdjustStockRequest req = new AdjustStockRequest();
        req.setDelta(5);
        req.setReason(AdjustmentReason.MANUAL_ADJUSTMENT);

        service.adjustLocationStock(COMPANY_ID, LOCATION_ID, PRODUCT_ID, OWNER_ID, req, null);

        verify(adjustmentRepository).save(any());
    }

    // ─── getProductLocationStocks ─────────────────────────────────────────────

    @Test
    void getProductLocationStocks_returnsAllEntriesForProduct() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product()));
        LocationStock ls = locationStock(location(), product(), 5, null);
        when(locationStockRepository.findAllByProductIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(List.of(ls));

        List<LocationStockResponse> result = service.getProductLocationStocks(COMPANY_ID, PRODUCT_ID, OWNER_ID);

        assertEquals(1, result.size());
        assertEquals(5, result.get(0).getStock());
    }

    @Test
    void getProductLocationStocks_productNotFound_throwsResourceNotFoundException() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.getProductLocationStocks(COMPANY_ID, PRODUCT_ID, OWNER_ID));
    }

    // ─── setLocationStock — stock increase (delta > 0, no alert) ─────────────

    @Test
    void setLocationStock_positiveIncrease_noAlert() {
        InventoryLocation location = location();
        Product product = product();
        LocationStock existing = locationStock(location, product, 5, null);

        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(LOCATION_ID, PRODUCT_ID, null))
                .thenReturn(Optional.of(existing));
        when(productRepository.getReferenceById(PRODUCT_ID)).thenReturn(product);
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user());

        SetLocationStockRequest req = new SetLocationStockRequest();
        req.setStock(15);

        LocationStockResponse resp = service.setLocationStock(COMPANY_ID, LOCATION_ID, PRODUCT_ID, OWNER_ID, req, null);

        verify(locationStockRepository).setStock(LOCATION_STOCK_ID, 15, null);
        verify(adjustmentRepository).save(any());
        // no alert for positive delta
        verify(stockAlertService, org.mockito.Mockito.never()).checkAndAlertLocation(
                any(), any(), any(), any(), any(), anyInt(), any());
        assertEquals(15, resp.getStock());
    }

    // ─── setLocationStock — no delta (same value) ─────────────────────────────

    @Test
    void setLocationStock_sameDelta_noAdjustmentSaved() {
        InventoryLocation location = location();
        Product product = product();
        LocationStock existing = locationStock(location, product, 10, null);

        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(LOCATION_ID, PRODUCT_ID, null))
                .thenReturn(Optional.of(existing));

        SetLocationStockRequest req = new SetLocationStockRequest();
        req.setStock(10); // same as current

        service.setLocationStock(COMPANY_ID, LOCATION_ID, PRODUCT_ID, OWNER_ID, req, null);

        verify(adjustmentRepository, org.mockito.Mockito.never()).save(any());
    }

    // ─── adjustLocationStock — negative delta triggers alert ─────────────────

    @Test
    void adjustLocationStock_negativeDelta_triggersAlert() {
        InventoryLocation location = location();
        Product product = product();
        LocationStock ls = locationStock(location, product, 10, 3);

        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(LOCATION_ID, PRODUCT_ID, null))
                .thenReturn(Optional.of(ls));
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(locationStockRepository.adjustStock(eq(LOCATION_STOCK_ID), eq(-8))).thenReturn(1);
        when(productRepository.getReferenceById(PRODUCT_ID)).thenReturn(product);
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user());

        AdjustStockRequest req = new AdjustStockRequest();
        req.setDelta(-8);
        req.setReason(AdjustmentReason.MANUAL_ADJUSTMENT);

        service.adjustLocationStock(COMPANY_ID, LOCATION_ID, PRODUCT_ID, OWNER_ID, req, null);

        verify(stockAlertService).checkAndAlertLocation(
                LOCATION_STOCK_ID, "Toronto Warehouse", PRODUCT_ID, "Desk", null, 2, 3);
    }

    // ─── updateLocation — code change, no conflict ────────────────────────────

    @Test
    void updateLocation_codeChangeNoConflict_updatesCode() {
        InventoryLocation existing = location();
        existing.setCode("TOR-1");
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(locationRepository.existsByCodeAndCompanyIdAndIdNot("VAN-2", COMPANY_ID, LOCATION_ID)).thenReturn(false);
        when(locationRepository.save(any(InventoryLocation.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateLocationRequest req = new UpdateLocationRequest();
        req.setCode("VAN-2");

        service.updateLocation(COMPANY_ID, LOCATION_ID, OWNER_ID, req);

        assertEquals("VAN-2", existing.getCode());
    }

    // ─── setLocationStock with variant ────────────────────────────────────────

    private static final UUID VARIANT_ID = TestIds.uuid(10);

    @Test
    void setLocationStock_withVariant_createsVariantStock() {
        InventoryLocation location = location();
        Product product = product();
        ProductVariant variant = variant();

        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(LOCATION_ID, PRODUCT_ID, VARIANT_ID))
                .thenReturn(Optional.empty());
        when(productRepository.getReferenceById(PRODUCT_ID)).thenReturn(product);
        when(variantRepository.getReferenceById(VARIANT_ID)).thenReturn(variant);
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user());
        when(locationStockRepository.save(any(LocationStock.class))).thenAnswer(inv -> {
            LocationStock ls = inv.getArgument(0);
            ls.setId(TestIds.uuid(99));
            ls.setVariant(variant);
            return ls;
        });

        SetLocationStockRequest req = new SetLocationStockRequest();
        req.setStock(7);

        LocationStockResponse resp = service.setLocationStock(
                COMPANY_ID, LOCATION_ID, PRODUCT_ID, OWNER_ID, req, VARIANT_ID);

        assertNotNull(resp);
        verify(variantRepository).findByIdAndProductId(VARIANT_ID, PRODUCT_ID);
        verify(locationStockRepository).save(any(LocationStock.class));
    }

    private ProductVariant variant() {
        ProductVariant v = new ProductVariant();
        v.setId(VARIANT_ID);
        v.setProduct(product());
        v.setSku("DESK-BLACK");
        return v;
    }
}
