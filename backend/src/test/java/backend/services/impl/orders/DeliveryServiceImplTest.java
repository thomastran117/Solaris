package backend.services.impl.orders;

import backend.events.order.DeliveryLocationEvent;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Order;
import backend.models.enums.OrderStatus;
import backend.repositories.OrderRepository;
import backend.services.intf.CacheService;
import backend.services.intf.orders.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeliveryServiceImplTest {

    private CacheService cacheService;
    private StringRedisTemplate stringRedisTemplate;
    private OrderRepository orderRepository;
    private OrderService orderService;
    private SimpMessagingTemplate messagingTemplate;
    private ObjectMapper objectMapper;
    private DeliveryServiceImpl service;

    private UUID orderId;
    private UUID driverId;
    private Order order;

    @BeforeEach
    void setUp() {
        cacheService = mock(CacheService.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        orderRepository = mock(OrderRepository.class);
        orderService = mock(OrderService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new DeliveryServiceImpl(cacheService, stringRedisTemplate, orderRepository,
                orderService, messagingTemplate, objectMapper);

        orderId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        order = new Order();
        order.setStatus(OrderStatus.SHIPPED);
        order.setAssignedDriverId(driverId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        // Rate-limit gate passes by default so other tests don't need to stub it.
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
    }

    private DeliveryLocationEvent freshEvent() {
        return new DeliveryLocationEvent(37.7749, -122.4194, Instant.now());
    }

    // ── processLocationUpdate ─────────────────────────────────────────────────

    @Test
    void processLocationUpdate_storesInRedis() {
        service.processLocationUpdate(orderId, freshEvent(), driverId);
        verify(cacheService, times(1)).set(eq("delivery:location:" + orderId), anyString(), eq(600L));
    }

    @Test
    void processLocationUpdate_publishesSseEventAfterStore() throws Exception {
        service.processLocationUpdate(orderId, freshEvent(), driverId);
        verify(stringRedisTemplate, times(1)).convertAndSend(eq("order:stream:" + orderId), anyString());
    }

    @Test
    void processLocationUpdate_doesNotThrowOnCacheFailure() {
        doThrow(new RuntimeException("Redis down")).when(cacheService).set(anyString(), anyString(), anyLong());
        assertDoesNotThrow(() -> service.processLocationUpdate(orderId, freshEvent(), driverId));
    }

    @Test
    void processLocationUpdate_doesNotThrowOnRedisPublishFailure() {
        when(stringRedisTemplate.convertAndSend(anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis down"));
        assertDoesNotThrow(() -> service.processLocationUpdate(orderId, freshEvent(), driverId));
    }

    @Test
    void processLocationUpdate_rejectsOutOfRangeLatitude() {
        DeliveryLocationEvent bad = new DeliveryLocationEvent(91.0, 0.0, Instant.now());
        assertThrows(IllegalArgumentException.class,
                () -> service.processLocationUpdate(orderId, bad, driverId));
        verify(cacheService, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    void processLocationUpdate_rejectsOutOfRangeLongitude() {
        DeliveryLocationEvent bad = new DeliveryLocationEvent(0.0, 181.0, Instant.now());
        assertThrows(IllegalArgumentException.class,
                () -> service.processLocationUpdate(orderId, bad, driverId));
        verify(cacheService, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    void processLocationUpdate_ignoresOutOfOrderTimestamp() throws Exception {
        Instant base = Instant.now();
        ObjectNode cached = objectMapper.createObjectNode();
        cached.put("clientTimestamp", base.toString());
        cached.put("serverReceivedAt", base.toString());
        when(cacheService.get("delivery:location:" + orderId)).thenReturn(cached.toString());

        DeliveryLocationEvent stale = new DeliveryLocationEvent(1.0, 1.0, base.minusSeconds(5));
        service.processLocationUpdate(orderId, stale, driverId);

        verify(cacheService, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    void processLocationUpdate_rateLimitedWhenLockAlreadyHeld() {
        // H17: tryLock returns false → too soon since last accepted update, skip entirely.
        when(cacheService.tryLock(eq("delivery:ratelimit:" + orderId), anyString(), anyLong()))
                .thenReturn(false);

        service.processLocationUpdate(orderId, freshEvent(), driverId);

        verify(cacheService, never()).set(eq("delivery:location:" + orderId), anyString(), anyLong());
        verify(stringRedisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void processLocationUpdate_passesRateLimitWhenLockAcquired() {
        // H17: tryLock returns true → window has elapsed, update proceeds.
        when(cacheService.tryLock(eq("delivery:ratelimit:" + orderId), anyString(), anyLong()))
                .thenReturn(true);

        service.processLocationUpdate(orderId, freshEvent(), driverId);

        verify(cacheService, times(1)).set(eq("delivery:location:" + orderId), anyString(), anyLong());
        verify(stringRedisTemplate, times(1)).convertAndSend(anyString(), anyString());
    }

    // ── processDriverStatus ───────────────────────────────────────────────────

    @Test
    void processDriverStatus_pickedUp_delegatesToOrderService() {
        service.processDriverStatus(orderId, "PICKED_UP", driverId);
        verify(orderService, times(1)).markPickedUpByDriver(orderId, driverId);
        verify(cacheService, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    void processDriverStatus_arrived_delegatesToOrderService() {
        service.processDriverStatus(orderId, "ARRIVED", driverId);
        verify(orderService, times(1)).markArrivedByDriver(orderId, driverId);
    }

    @Test
    void processDriverStatus_delivered_delegatesToOrderService() {
        service.processDriverStatus(orderId, "DELIVERED", driverId);
        verify(orderService, times(1)).markDeliveredByDriver(orderId, driverId);
    }

    // ── assignDriver ──────────────────────────────────────────────────────────

    @Test
    void assignDriver_setsDriverIdAndSavesOrder() {
        UUID companyId = UUID.randomUUID();
        UUID newDriver = UUID.randomUUID();
        when(orderRepository.findByIdAndProductCompanyId(orderId, companyId)).thenReturn(Optional.of(order));

        service.assignDriver(companyId, orderId, newDriver, UUID.randomUUID());

        assertEquals(newDriver, order.getAssignedDriverId());
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void assignDriver_pushesAssignmentToDriver() {
        UUID companyId = UUID.randomUUID();
        UUID newDriver = UUID.randomUUID();
        when(orderRepository.findByIdAndProductCompanyId(orderId, companyId)).thenReturn(Optional.of(order));

        service.assignDriver(companyId, orderId, newDriver, UUID.randomUUID());

        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate, times(1))
                .convertAndSendToUser(eq(newDriver.toString()), destCaptor.capture(), anyString());
        assertEquals("/queue/assignments", destCaptor.getValue());
    }

    @Test
    void assignDriver_throwsResourceNotFoundWhenOrderMissing() {
        UUID companyId = UUID.randomUUID();
        when(orderRepository.findByIdAndProductCompanyId(any(), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.assignDriver(companyId, orderId, driverId, UUID.randomUUID()));
    }
}
