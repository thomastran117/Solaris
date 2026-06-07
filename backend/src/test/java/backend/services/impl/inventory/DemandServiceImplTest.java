package backend.services.impl.inventory;

import backend.dtos.responses.analytics.DemandEntry;
import backend.dtos.responses.analytics.HotProductsResponse;
import backend.exceptions.http.BadRequestException;
import backend.repositories.ProductRepository;
import backend.repositories.projections.ProductDemandProjection;
import backend.services.intf.CacheService;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemandServiceImplTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID OWNER_ID = TestIds.uuid(2);

    private CompanyAccessService companyAccessService;
    private ProductRepository productRepository;
    private CacheService cacheService;
    private ObjectMapper objectMapper;
    private DemandServiceImpl service;

    @BeforeEach
    void setUp() {
        companyAccessService = mock(CompanyAccessService.class);
        productRepository = mock(ProductRepository.class);
        cacheService = mock(CacheService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new DemandServiceImpl(companyAccessService, productRepository, cacheService, objectMapper);
        ReflectionTestUtils.setField(service, "cacheTtl1h", 300);
        ReflectionTestUtils.setField(service, "cacheTtl24h", 900);
    }

    @Test
    void getHotProducts_invalidWindowThrows() {
        assertThrows(BadRequestException.class,
                () -> service.getHotProducts(COMPANY_ID, OWNER_ID, "7d", 5));
    }

    @Test
    void getHotProducts_cacheDeserializeError_fallsThroughToCompute() {
        when(cacheService.get("demand:hot:24h:" + COMPANY_ID)).thenReturn("{invalid-json}");
        when(productRepository.findTopByDemandSince(eq(COMPANY_ID), any(), eq(50)))
                .thenReturn(List.of(demandProjection(TestIds.uuid(30), "Widget", 5L, new BigDecimal("50.00"))));

        HotProductsResponse response = service.getHotProducts(COMPANY_ID, OWNER_ID, "24h", 5);

        assertEquals(1, response.products().size());
        verify(productRepository).findTopByDemandSince(eq(COMPANY_ID), any(), eq(50));
    }

    @Test
    void getHotProducts_24hWindow_noBaselineFetch() {
        when(cacheService.get(anyString())).thenReturn(null);
        UUID prodId = TestIds.uuid(40);
        when(productRepository.findTopByDemandSince(eq(COMPANY_ID), any(), eq(50)))
                .thenReturn(List.of(demandProjection(prodId, "Chair", 48L, new BigDecimal("480.00"))));

        HotProductsResponse response = service.getHotProducts(COMPANY_ID, OWNER_ID, "24h", 10);

        assertEquals(1, response.products().size());
        DemandEntry entry = response.products().get(0);
        assertEquals(2.0, entry.velocityPerHour(), 0.001); // 48 / 24
        assertEquals(1.0, entry.accelerationRatio(), 0.001); // no acceleration for 24h
        // Only one DB call — no baseline fetch
        verify(productRepository).findTopByDemandSince(eq(COMPANY_ID), any(), eq(50));
    }

    @Test
    void getHotProducts_nullTotalUnitsSold_treatedAsZero() {
        when(cacheService.get(anyString())).thenReturn(null);
        when(productRepository.findTopByDemandSince(eq(COMPANY_ID), any(), eq(50)))
                .thenReturn(List.of(demandProjection(TestIds.uuid(50), "Ghost", null, BigDecimal.ZERO)));

        HotProductsResponse response = service.getHotProducts(COMPANY_ID, OWNER_ID, "24h", 5);

        assertEquals(1, response.products().size());
        assertEquals(0L, response.products().get(0).unitsSold());
    }

    @Test
    void getHotProducts_cacheWriteError_gracefullyIgnored() throws Exception {
        when(cacheService.get(anyString())).thenReturn(null);
        when(productRepository.findTopByDemandSince(eq(COMPANY_ID), any(), eq(50)))
                .thenReturn(List.of(demandProjection(TestIds.uuid(60), "Lamp", 6L, new BigDecimal("60.00"))));
        doThrow(new RuntimeException("Redis down")).when(cacheService).set(anyString(), anyString(), anyLong());

        // Should not throw even though cache write fails
        HotProductsResponse response = service.getHotProducts(COMPANY_ID, OWNER_ID, "24h", 5);

        assertEquals(1, response.products().size());
    }

    @Test
    void refreshCache_delegatesToComputeAndCache() {
        when(productRepository.findTopByDemandSince(eq(COMPANY_ID), any(), eq(50)))
                .thenReturn(List.of(demandProjection(TestIds.uuid(70), "Shelf", 10L, new BigDecimal("100.00"))));

        service.refreshCache(COMPANY_ID, "24h");

        verify(productRepository).findTopByDemandSince(eq(COMPANY_ID), any(), eq(50));
        verify(cacheService).set(eq("demand:hot:24h:" + COMPANY_ID), anyString(), eq(900L));
    }

    @Test
    void getHotProducts_1hWindow_nullBaselineEntry_usesZero() {
        when(cacheService.get(anyString())).thenReturn(null);
        UUID prodId = TestIds.uuid(80);
        // 1h window: first call returns the 1h results, second call (baseline 24h) returns empty
        when(productRepository.findTopByDemandSince(eq(COMPANY_ID), any(), eq(50)))
                .thenReturn(
                        List.of(demandProjection(prodId, "Lamp", 6L, new BigDecimal("60.00"))),
                        List.of() // no 24h data — acceleration should be near max
                );

        HotProductsResponse response = service.getHotProducts(COMPANY_ID, OWNER_ID, "1h", 5);

        assertEquals(1, response.products().size());
        // velocity = 6/1 = 6.0, velocity24hAvg = 0/24 → epsilon 0.001, acceleration ≈ 6000
        assertTrue(response.products().get(0).accelerationRatio() > 100.0);
    }

    @Test
    void getHotProducts_usesCacheAndSlicesToRequestedLimit() throws Exception {
        HotProductsResponse cached = new HotProductsResponse(
                "24h",
                Instant.parse("2026-05-19T12:00:00Z"),
                Instant.parse("2026-05-18T12:00:00Z"),
                List.of(
                        entry(TestIds.uuid(10), 1),
                        entry(TestIds.uuid(11), 2),
                        entry(TestIds.uuid(12), 3)
                ));
        when(cacheService.get("demand:hot:24h:" + COMPANY_ID))
                .thenReturn(objectMapper.writeValueAsString(cached));

        HotProductsResponse response = service.getHotProducts(COMPANY_ID, OWNER_ID, "24h", 2);

        assertEquals(2, response.products().size());
        verify(productRepository, never()).findTopByDemandSince(any(), any(), anyInt());
    }

    @Test
    void getHotProducts_computesAccelerationAndCachesResult() {
        when(cacheService.get(anyString())).thenReturn(null);
        when(productRepository.findTopByDemandSince(eq(COMPANY_ID), any(), eq(50)))
                .thenReturn(
                        List.of(demandProjection(TestIds.uuid(20), "Desk", 12L, new BigDecimal("240.00"))),
                        List.of(demandProjection(TestIds.uuid(20), "Desk", 24L, new BigDecimal("480.00")))
                );

        HotProductsResponse response = service.getHotProducts(COMPANY_ID, OWNER_ID, "1h", 1);

        assertEquals(1, response.products().size());
        assertEquals(12.0, response.products().get(0).velocityPerHour());
        assertTrue(response.products().get(0).accelerationRatio() > 10.0);
        verify(cacheService).set(eq("demand:hot:1h:" + COMPANY_ID), anyString(), eq(300L));
    }

    private DemandEntry entry(UUID id, int rank) {
        return new DemandEntry(id, "Product " + rank, "SKU-" + rank, BigDecimal.TEN, "USD",
                rank, BigDecimal.valueOf(rank * 10L), rank, 1.0, rank);
    }

    private ProductDemandProjection demandProjection(UUID id, String name, Long units, BigDecimal revenue) {
        return new ProductDemandProjection() {
            @Override public UUID getProductId() { return id; }
            @Override public String getProductName() { return name; }
            @Override public String getSku() { return "SKU-" + name; }
            @Override public BigDecimal getPrice() { return BigDecimal.TEN; }
            @Override public String getCurrency() { return "USD"; }
            @Override public Long getTotalUnitsSold() { return units; }
            @Override public BigDecimal getTotalRevenue() { return revenue; }
        };
    }
}
