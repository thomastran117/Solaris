package backend.services.impl.orders;

import backend.events.order.DeliveryLocationEvent;
import backend.events.order.SseStatusUpdateEvent;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Order;
import backend.repositories.OrderRepository;
import backend.services.intf.CacheService;
import backend.services.intf.orders.DeliveryService;
import backend.services.intf.orders.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class DeliveryServiceImpl implements DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryServiceImpl.class);

    private static final long LOCATION_TTL_SECONDS = 600;
    private static final long RATE_LIMIT_SECONDS = 3;

    private final CacheService cacheService;
    private final StringRedisTemplate stringRedisTemplate;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public DeliveryServiceImpl(CacheService cacheService,
                               StringRedisTemplate stringRedisTemplate,
                               OrderRepository orderRepository,
                               OrderService orderService,
                               SimpMessagingTemplate messagingTemplate,
                               ObjectMapper objectMapper) {
        this.cacheService = cacheService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void processLocationUpdate(UUID orderId, DeliveryLocationEvent event, UUID driverId) {
        validateCoordinates(event.lat(), event.lng());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!driverId.equals(order.getAssignedDriverId())) {
            log.warn("[Delivery] Driver {} not assigned to order {}", driverId, orderId);
            return;
        }

        Instant serverNow = Instant.now();
        String cacheKey = "delivery:location:" + orderId;
        String existing = cacheService.get(cacheKey);

        if (existing != null) {
            try {
                ObjectNode cached = (ObjectNode) objectMapper.readTree(existing);
                Instant cachedClientTs = Instant.parse(cached.get("clientTimestamp").asText());
                if (!event.timestamp().isAfter(cachedClientTs)) return;

                Instant cachedServerTs = Instant.parse(cached.get("serverReceivedAt").asText());
                if (serverNow.getEpochSecond() - cachedServerTs.getEpochSecond() < RATE_LIMIT_SECONDS) return;
            } catch (Exception e) {
                log.warn("[Delivery] Failed to parse cached location for order {}: {}", orderId, e.getMessage());
            }
        }

        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("driverId", driverId.toString());
            payload.put("lat", event.lat());
            payload.put("lng", event.lng());
            payload.put("clientTimestamp", event.timestamp().toString());
            payload.put("serverReceivedAt", serverNow.toString());
            cacheService.set(cacheKey, payload.toString(), LOCATION_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("[Delivery] Failed to cache location for order {}: {}", orderId, e.getMessage());
            return;
        }

        try {
            SseStatusUpdateEvent sseEvent = new SseStatusUpdateEvent(
                    UUID.randomUUID(), orderId, order.getStatus().name(),
                    order.getTrackingNumber(), order.getCarrier(),
                    event.lat(), event.lng(), null, serverNow, "location_update");
            stringRedisTemplate.convertAndSend(
                    "order:stream:" + orderId,
                    objectMapper.writeValueAsString(sseEvent));
        } catch (Exception e) {
            log.warn("[Delivery] Failed to publish location SSE for order {}: {}", orderId, e.getMessage());
        }
    }

    @Override
    public void processDriverStatus(UUID orderId, String status, UUID driverId) {
        switch (status) {
            case "PICKED_UP" -> orderService.markPickedUpByDriver(orderId, driverId);
            case "ARRIVED"   -> orderService.markArrivedByDriver(orderId, driverId);
            case "DELIVERED" -> orderService.markDeliveredByDriver(orderId, driverId);
            default -> log.warn("[Delivery] Unknown driver status '{}' for order {}", status, orderId);
        }
    }

    @Override
    public void assignDriver(UUID companyId, UUID orderId, UUID driverUserId, UUID ownerId) {
        Order order = orderRepository.findByIdAndProductCompanyId(orderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        order.setAssignedDriverId(driverUserId);
        orderRepository.save(order);

        try {
            ObjectNode assignment = objectMapper.createObjectNode();
            assignment.put("orderId", orderId.toString());
            assignment.put("assignedAt", Instant.now().toString());
            messagingTemplate.convertAndSendToUser(
                    driverUserId.toString(),
                    "/queue/assignments",
                    assignment.toString());
        } catch (Exception e) {
            log.warn("[Delivery] Failed to push assignment to driver {}: {}", driverUserId, e.getMessage());
        }
    }

    private void validateCoordinates(double lat, double lng) {
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Invalid latitude: " + lat);
        }
        if (lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Invalid longitude: " + lng);
        }
    }
}
