package backend.services.impl.orders;

import backend.events.order.SseStatusUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class OrderSseService {

    private static final Logger log = LoggerFactory.getLogger(OrderSseService.class);

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public OrderSseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(UUID orderId, SseEmitter emitter) {
        emitters.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    public void deregister(UUID orderId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(orderId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(orderId, list);
            }
        }
    }

    public void broadcast(UUID orderId, SseStatusUpdateEvent event) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(orderId);
        if (list == null || list.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("[SSE] Failed to serialize event for order {}: {}", orderId, e.getMessage());
            return;
        }

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(event.eventType()).data(json));
            } catch (Exception e) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    emitters.remove(orderId, list);
                }
            }
        }
    }

    @Scheduled(fixedDelay = 25_000)
    public void sendHeartbeats() {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of("ts", Instant.now().toString()));
        } catch (Exception e) {
            return;
        }

        emitters.forEach((orderId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data(payload));
                } catch (Exception e) {
                    list.remove(emitter);
                    if (list.isEmpty()) {
                        emitters.remove(orderId, list);
                    }
                }
            }
        });
    }
}
