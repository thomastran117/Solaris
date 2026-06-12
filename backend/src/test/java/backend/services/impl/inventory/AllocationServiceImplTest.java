package backend.services.impl.inventory;

import backend.models.core.InventoryLocation;
import backend.models.core.LocationStock;
import backend.models.enums.AllocationStrategy;
import backend.repositories.LocationStockRepository;
import backend.services.intf.inventory.AllocationService.AllocationResult;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AllocationServiceImplTest {

    private static final UUID PRODUCT_ID = TestIds.uuid(1);
    private static final UUID STOCK_A = TestIds.uuid(2);
    private static final UUID STOCK_B = TestIds.uuid(3);

    private LocationStockRepository locationStockRepository;
    private AllocationServiceImpl service;

    @BeforeEach
    void setUp() {
        locationStockRepository = mock(LocationStockRepository.class);
        service = new AllocationServiceImpl(locationStockRepository);
    }

    @Test
    void allocate_nearestWithoutBuyerCoordsFallsBackToHighestStockAndSplits() {
        when(locationStockRepository.findTopByProductStockDesc(eq(PRODUCT_ID), any()))
                .thenReturn(List.of(locationStock(STOCK_A, 2), locationStock(STOCK_B, 1)));
        when(locationStockRepository.decrementStock(STOCK_A, 2)).thenReturn(1);
        when(locationStockRepository.decrementStock(STOCK_B, 1)).thenReturn(1);

        List<AllocationResult> results = service.allocate(
                PRODUCT_ID, null, 3, AllocationStrategy.NEAREST, null, null);

        assertEquals(2, results.size());
        assertEquals(2, results.get(0).allocatedQty());
        assertEquals(1, results.get(1).allocatedQty());
        verify(locationStockRepository, never()).findByProductOrderedByDistance(any(), any(double.class), any(double.class), any());
    }

    @Test
    void allocate_whenInsufficientStockRestoresPartialDecrementsAndReturnsEmpty() {
        when(locationStockRepository.findTopByProductStockDesc(eq(PRODUCT_ID), any()))
                .thenReturn(List.of(locationStock(STOCK_A, 2), locationStock(STOCK_B, 1)));
        when(locationStockRepository.decrementStock(STOCK_A, 2)).thenReturn(1);
        when(locationStockRepository.decrementStock(STOCK_B, 1)).thenReturn(1);

        List<AllocationResult> results = service.allocate(
                PRODUCT_ID, null, 5, AllocationStrategy.HIGHEST_STOCK, null, null);

        assertTrue(results.isEmpty());
        verify(locationStockRepository).restoreStock(STOCK_A, 2);
        verify(locationStockRepository).restoreStock(STOCK_B, 1);
    }

    @Test
    void allocate_nearestWithBuyerCoords_usesDistanceSortedResults() {
        when(locationStockRepository.findByProductOrderedByDistance(eq(PRODUCT_ID), eq(43.0), eq(-79.0), any()))
                .thenReturn(List.of(locationStock(STOCK_A, 5)));
        when(locationStockRepository.decrementStock(STOCK_A, 3)).thenReturn(1);

        List<AllocationResult> results = service.allocate(
                PRODUCT_ID, null, 3, AllocationStrategy.NEAREST, 43.0, -79.0);

        assertEquals(1, results.size());
        assertEquals(3, results.get(0).allocatedQty());
    }

    @Test
    void allocate_nearestWithCoords_emptyDistanceResult_fallsBackToHighestStock() {
        when(locationStockRepository.findByProductOrderedByDistance(eq(PRODUCT_ID), eq(43.0), eq(-79.0), any()))
                .thenReturn(List.of());
        when(locationStockRepository.findTopByProductStockDesc(eq(PRODUCT_ID), any()))
                .thenReturn(List.of(locationStock(STOCK_A, 4)));
        when(locationStockRepository.decrementStock(STOCK_A, 2)).thenReturn(1);

        List<AllocationResult> results = service.allocate(
                PRODUCT_ID, null, 2, AllocationStrategy.NEAREST, 43.0, -79.0);

        assertEquals(1, results.size());
    }

    @Test
    void allocate_cheapestStrategy_usesProductCostSortedResults() {
        when(locationStockRepository.findByProductOrderedByCost(eq(PRODUCT_ID), any()))
                .thenReturn(List.of(locationStock(STOCK_A, 10)));
        when(locationStockRepository.decrementStock(STOCK_A, 4)).thenReturn(1);

        List<AllocationResult> results = service.allocate(
                PRODUCT_ID, null, 4, AllocationStrategy.CHEAPEST, null, null);

        assertEquals(1, results.size());
        assertEquals(4, results.get(0).allocatedQty());
    }

    @Test
    void allocate_cheapestStrategy_emptyResult_fallsBackToHighestStock() {
        when(locationStockRepository.findByProductOrderedByCost(eq(PRODUCT_ID), any()))
                .thenReturn(List.of());
        when(locationStockRepository.findTopByProductStockDesc(eq(PRODUCT_ID), any()))
                .thenReturn(List.of(locationStock(STOCK_A, 5)));
        when(locationStockRepository.decrementStock(STOCK_A, 5)).thenReturn(1);

        List<AllocationResult> results = service.allocate(
                PRODUCT_ID, null, 5, AllocationStrategy.CHEAPEST, null, null);

        assertEquals(1, results.size());
    }

    @Test
    void allocate_highestStockWithVariant_usesVariantQuery() {
        UUID variantId = TestIds.uuid(10);
        when(locationStockRepository.findTopByVariantStockDesc(eq(PRODUCT_ID), eq(variantId), any()))
                .thenReturn(List.of(locationStock(STOCK_A, 3)));
        when(locationStockRepository.decrementStock(STOCK_A, 3)).thenReturn(1);

        List<AllocationResult> results = service.allocate(
                PRODUCT_ID, variantId, 3, AllocationStrategy.HIGHEST_STOCK, null, null);

        assertEquals(1, results.size());
    }

    @Test
    void allocate_noCandidates_returnsEmpty() {
        when(locationStockRepository.findTopByProductStockDesc(eq(PRODUCT_ID), any()))
                .thenReturn(List.of());

        List<AllocationResult> results = service.allocate(
                PRODUCT_ID, null, 2, AllocationStrategy.HIGHEST_STOCK, null, null);

        assertTrue(results.isEmpty());
    }

    @Test
    void allocate_nearestWithVariantAndCoords_usesVariantDistanceQuery() {
        UUID variantId = TestIds.uuid(11);
        when(locationStockRepository.findByVariantOrderedByDistance(eq(PRODUCT_ID), eq(variantId), eq(43.0), eq(-79.0), any()))
                .thenReturn(List.of(locationStock(STOCK_A, 6)));
        when(locationStockRepository.decrementStock(STOCK_A, 2)).thenReturn(1);

        List<AllocationResult> results = service.allocate(
                PRODUCT_ID, variantId, 2, AllocationStrategy.NEAREST, 43.0, -79.0);

        assertEquals(1, results.size());
    }

    @Test
    void allocate_cheapestWithVariant_usesVariantCostQuery() {
        UUID variantId = TestIds.uuid(12);
        when(locationStockRepository.findByVariantOrderedByCost(eq(PRODUCT_ID), eq(variantId), any()))
                .thenReturn(List.of(locationStock(STOCK_A, 8)));
        when(locationStockRepository.decrementStock(STOCK_A, 3)).thenReturn(1);

        List<AllocationResult> results = service.allocate(
                PRODUCT_ID, variantId, 3, AllocationStrategy.CHEAPEST, null, null);

        assertEquals(1, results.size());
    }

    @Test
    void allocate_decrementReturnZero_skipsToNextCandidate() {
        when(locationStockRepository.findTopByProductStockDesc(eq(PRODUCT_ID), any()))
                .thenReturn(List.of(locationStock(STOCK_A, 3), locationStock(STOCK_B, 2)));
        // STOCK_A decrement returns 0 → race condition, skip
        when(locationStockRepository.decrementStock(STOCK_A, 3)).thenReturn(0);
        when(locationStockRepository.decrementStock(STOCK_B, 2)).thenReturn(1);

        List<AllocationResult> results = service.allocate(
                PRODUCT_ID, null, 3, AllocationStrategy.HIGHEST_STOCK, null, null);

        // STOCK_A skipped (decrementStock returns 0 — race condition); remaining stays 3
        // STOCK_B provides 2 → remaining=1 → not fully satisfied → rollback
        assertTrue(results.isEmpty());
        verify(locationStockRepository).restoreStock(STOCK_B, 2);
    }

    private LocationStock locationStock(UUID id, int stock) {
        LocationStock ls = new LocationStock();
        ls.setId(id);
        ls.setStock(stock);
        InventoryLocation loc = new InventoryLocation();
        loc.setId(UUID.randomUUID());
        loc.setName("Location " + stock);
        ls.setLocation(loc);
        return ls;
    }
}
