package backend.services.impl.inventory;

import backend.dtos.responses.inventory.AvailabilityEstimateResponse;
import backend.models.core.Company;
import backend.models.core.InventoryLocation;
import backend.models.core.LocationStock;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.LocationType;
import backend.repositories.LocationStockRepository;
import backend.repositories.ProductRepository;
import backend.services.intf.CacheService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvailabilityEstimateServiceImplTest {

    private static final UUID MARKETPLACE_ID = TestIds.uuid(1);
    private static final UUID PRODUCT_ID = TestIds.uuid(2);

    private ProductRepository productRepository;
    private LocationStockRepository locationStockRepository;
    private CacheService cacheService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        locationStockRepository = mock(LocationStockRepository.class);
        cacheService = mock(CacheService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void estimateForMarketplace_returnsCachedValueWhenPresent() throws Exception {
        Product product = marketplaceProduct();
        AvailabilityEstimateResponse cached = AvailabilityEstimateResponse.outOfStock();
        when(productRepository.findByIdAndMarketplaceId(PRODUCT_ID, MARKETPLACE_ID)).thenReturn(Optional.of(product));
        when(cacheService.get(anyString())).thenReturn(objectMapper.writeValueAsString(cached));

        AvailabilityEstimateResponse response = new AvailabilityEstimateServiceImpl(
                productRepository, locationStockRepository, cacheService, objectMapper)
                .estimateForMarketplace(MARKETPLACE_ID, PRODUCT_ID, null, null, null);

        assertEquals(false, response.inStock());
        verify(locationStockRepository, never()).findStockedByProduct(any());
    }

    @Test
    void estimateForMarketplace_buildsPickupAwareResponseAndCachesIt() {
        Product product = marketplaceProduct();
        InventoryLocation hybrid = new InventoryLocation();
        hybrid.setId(TestIds.uuid(10));
        hybrid.setName("Downtown");
        hybrid.setCity("Toronto");
        hybrid.setCountry("Canada");
        hybrid.setLatitude(43.65);
        hybrid.setLongitude(-79.38);
        hybrid.setHandlingDays(1);
        hybrid.setPickupReadyHours(4);
        hybrid.setType(LocationType.HYBRID);

        LocationStock stock = new LocationStock();
        stock.setLocation(hybrid);
        stock.setProduct(product);
        stock.setStock(5);

        when(productRepository.findByIdAndMarketplaceId(PRODUCT_ID, MARKETPLACE_ID)).thenReturn(Optional.of(product));
        when(cacheService.get(anyString())).thenReturn(null);
        when(locationStockRepository.findByProductOrderedByDistance(eq(PRODUCT_ID), eq(43.65), eq(-79.38), any()))
                .thenReturn(List.of(stock));

        AvailabilityEstimateResponse response = new AvailabilityEstimateServiceImpl(
                productRepository,
                locationStockRepository,
                cacheService,
                objectMapper,
                Clock.fixed(Instant.parse("2026-05-19T00:00:00Z"), ZoneOffset.UTC))
                .estimateForMarketplace(MARKETPLACE_ID, PRODUCT_ID, null, 43.65, -79.38);

        assertTrue(response.inStock());
        assertNotNull(response.nearestSource());
        assertNotNull(response.pickup());
        assertNotNull(response.etaDaysMin());
        assertEquals(LocalDate.of(2026, 5, 19).plusDays(response.etaDaysMin()), response.etaDateMin());
        verify(cacheService).set(anyString(), anyString(), eq(300L));
    }

    @Test
    void estimateForMarketplace_noStockedLocations_returnsOutOfStock() {
        Product product = marketplaceProduct();
        when(productRepository.findByIdAndMarketplaceId(PRODUCT_ID, MARKETPLACE_ID)).thenReturn(Optional.of(product));
        when(cacheService.get(anyString())).thenReturn(null);
        when(locationStockRepository.findStockedByProduct(PRODUCT_ID)).thenReturn(List.of());

        AvailabilityEstimateResponse response = new AvailabilityEstimateServiceImpl(
                productRepository, locationStockRepository, cacheService, objectMapper)
                .estimateForMarketplace(MARKETPLACE_ID, PRODUCT_ID, null, null, null);

        assertEquals(false, response.inStock());
        assertNull(response.nearestSource());
    }

    @Test
    void estimateForMarketplace_noBuyerCoords_usesProductFallback() {
        Product product = marketplaceProduct();
        InventoryLocation warehouse = warehouseLocation();

        LocationStock stock = new LocationStock();
        stock.setLocation(warehouse);
        stock.setProduct(product);
        stock.setStock(10);

        when(productRepository.findByIdAndMarketplaceId(PRODUCT_ID, MARKETPLACE_ID)).thenReturn(Optional.of(product));
        when(cacheService.get(anyString())).thenReturn(null);
        when(locationStockRepository.findStockedByProduct(PRODUCT_ID)).thenReturn(List.of(stock));

        AvailabilityEstimateResponse response = new AvailabilityEstimateServiceImpl(
                productRepository, locationStockRepository, cacheService, objectMapper)
                .estimateForMarketplace(MARKETPLACE_ID, PRODUCT_ID, null, null, null);

        assertTrue(response.inStock());
        assertNotNull(response.nearestSource());
        // No buyer coords → no pickup
        assertNull(response.pickup());
    }

    @Test
    void estimateForMarketplace_locationNoCoords_usesFallbackShippingDays() {
        Product product = marketplaceProduct();
        InventoryLocation noCoordLoc = new InventoryLocation();
        noCoordLoc.setId(TestIds.uuid(30));
        noCoordLoc.setName("Mystery Warehouse");
        noCoordLoc.setHandlingDays(0);
        noCoordLoc.setType(LocationType.WAREHOUSE);
        // latitude/longitude intentionally null

        LocationStock stock = new LocationStock();
        stock.setLocation(noCoordLoc);
        stock.setProduct(product);
        stock.setStock(3);

        when(productRepository.findByIdAndMarketplaceId(PRODUCT_ID, MARKETPLACE_ID)).thenReturn(Optional.of(product));
        when(cacheService.get(anyString())).thenReturn(null);
        when(locationStockRepository.findByProductOrderedByDistance(eq(PRODUCT_ID), eq(43.0), eq(-79.0), any()))
                .thenReturn(List.of(stock));

        AvailabilityEstimateResponse response = new AvailabilityEstimateServiceImpl(
                productRepository, locationStockRepository, cacheService, objectMapper)
                .estimateForMarketplace(MARKETPLACE_ID, PRODUCT_ID, null, 43.0, -79.0);

        assertTrue(response.inStock());
        assertNotNull(response.etaDaysMin());
    }

    @Test
    void estimateForMarketplace_byDistanceEmpty_fallsBackToFindStocked() {
        Product product = marketplaceProduct();
        InventoryLocation warehouse = warehouseLocation();
        LocationStock stock = new LocationStock();
        stock.setLocation(warehouse);
        stock.setProduct(product);
        stock.setStock(7);

        when(productRepository.findByIdAndMarketplaceId(PRODUCT_ID, MARKETPLACE_ID)).thenReturn(Optional.of(product));
        when(cacheService.get(anyString())).thenReturn(null);
        when(locationStockRepository.findByProductOrderedByDistance(eq(PRODUCT_ID), eq(43.0), eq(-79.0), any()))
                .thenReturn(List.of()); // empty → falls back
        when(locationStockRepository.findStockedByProduct(PRODUCT_ID)).thenReturn(List.of(stock));

        AvailabilityEstimateResponse response = new AvailabilityEstimateServiceImpl(
                productRepository, locationStockRepository, cacheService, objectMapper)
                .estimateForMarketplace(MARKETPLACE_ID, PRODUCT_ID, null, 43.0, -79.0);

        assertTrue(response.inStock());
    }

    @Test
    void estimateForMarketplace_warehouseLocationSkippedForPickup() {
        Product product = marketplaceProduct();
        // WAREHOUSE type → should not appear as pickup option
        InventoryLocation warehouse = warehouseLocation();
        warehouse.setLatitude(43.65);
        warehouse.setLongitude(-79.38);

        LocationStock stock = new LocationStock();
        stock.setLocation(warehouse);
        stock.setProduct(product);
        stock.setStock(5);

        when(productRepository.findByIdAndMarketplaceId(PRODUCT_ID, MARKETPLACE_ID)).thenReturn(Optional.of(product));
        when(cacheService.get(anyString())).thenReturn(null);
        when(locationStockRepository.findByProductOrderedByDistance(eq(PRODUCT_ID), eq(43.65), eq(-79.38), any()))
                .thenReturn(List.of(stock));

        AvailabilityEstimateResponse response = new AvailabilityEstimateServiceImpl(
                productRepository, locationStockRepository, cacheService, objectMapper)
                .estimateForMarketplace(MARKETPLACE_ID, PRODUCT_ID, null, 43.65, -79.38);

        assertTrue(response.inStock());
        assertNull(response.pickup());
    }

    private InventoryLocation warehouseLocation() {
        InventoryLocation loc = new InventoryLocation();
        loc.setId(TestIds.uuid(40));
        loc.setName("Central Warehouse");
        loc.setCity("Hamilton");
        loc.setCountry("Canada");
        loc.setHandlingDays(2);
        loc.setType(LocationType.WAREHOUSE);
        return loc;
    }

    private Product marketplaceProduct() {
        User owner = new User();
        owner.setId(TestIds.uuid(20));
        Company company = new Company();
        company.setId(TestIds.uuid(21));
        company.setOwner(owner);

        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setCompany(company);
        return product;
    }
}
