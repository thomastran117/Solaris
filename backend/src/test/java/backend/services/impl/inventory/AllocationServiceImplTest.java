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
