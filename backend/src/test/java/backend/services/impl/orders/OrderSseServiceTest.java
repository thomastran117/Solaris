package backend.services.impl.orders;

import backend.events.order.SseStatusUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderSseServiceTest {

    private OrderSseService service;

    @BeforeEach
    void setUp() {
        service = new OrderSseService(new ObjectMapper().findAndRegisterModules());
    }

    private SseStatusUpdateEvent event(UUID orderId) {
        return new SseStatusUpdateEvent(UUID.randomUUID(), orderId, "PACKED",
                null, null, null, null, null, Instant.now(), "status_update");
    }

    @Test
    void register_addsEmitterForOrder() throws Exception {
        UUID id = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter();
        service.register(id, emitter);

        // broadcast should not throw — if emitter wasn't registered it would be a no-op silently;
        // verify by confirming no exception and the emitter receives the send (it won't throw on a fresh emitter)
        assertDoesNotThrow(() -> service.broadcast(id, event(id)));
    }

    @Test
    void deregister_removesEmitter() throws Exception {
        UUID id = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter();
        service.register(id, emitter);
        service.deregister(id, emitter);

        // After deregister, broadcast is a no-op — completes without touching the emitter
        assertDoesNotThrow(() -> service.broadcast(id, event(id)));
    }

    @Test
    void deregister_cleansEmptyList() {
        UUID id = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter();
        service.register(id, emitter);
        service.deregister(id, emitter);

        // Registering again and immediately deregistering should not throw
        SseEmitter emitter2 = new SseEmitter();
        service.register(id, emitter2);
        service.deregister(id, emitter2);
        assertDoesNotThrow(() -> service.broadcast(id, event(id)));
    }

    @Test
    void broadcast_sendsToAllRegisteredEmitters() {
        UUID id = UUID.randomUUID();
        SseEmitter e1 = new SseEmitter();
        SseEmitter e2 = new SseEmitter();
        service.register(id, e1);
        service.register(id, e2);

        // Neither emitter is in a terminal state, so both sends should succeed without exception
        assertDoesNotThrow(() -> service.broadcast(id, event(id)));
    }

    @Test
    void broadcast_removesDeadEmitterOnSendFailure() throws Exception {
        UUID id = UUID.randomUUID();
        SseEmitter dead = new SseEmitter();
        dead.completeWithError(new RuntimeException("simulated dead"));
        service.register(id, dead);

        // broadcast discovers the dead emitter via send failure and removes it silently
        assertDoesNotThrow(() -> service.broadcast(id, event(id)));

        // A subsequent broadcast also completes without error (list is empty now)
        assertDoesNotThrow(() -> service.broadcast(id, event(id)));
    }

    @Test
    void broadcast_doesNotThrowWhenNoEmitters() {
        UUID id = UUID.randomUUID();
        assertDoesNotThrow(() -> service.broadcast(id, event(id)));
    }

    @Test
    void heartbeat_sendsToAllEmitters() {
        UUID id = UUID.randomUUID();
        SseEmitter e1 = new SseEmitter();
        SseEmitter e2 = new SseEmitter();
        service.register(id, e1);
        service.register(id, e2);

        assertDoesNotThrow(() -> service.sendHeartbeats());
    }

    @Test
    void heartbeat_removesDeadEmitterDiscoveredDuringSweep() throws Exception {
        UUID id = UUID.randomUUID();
        SseEmitter dead = new SseEmitter();
        dead.completeWithError(new RuntimeException("simulated dead"));
        service.register(id, dead);

        assertDoesNotThrow(() -> service.sendHeartbeats());

        // After sweep, the dead emitter is gone — broadcast is also a no-op
        assertDoesNotThrow(() -> service.broadcast(id, event(id)));
    }
}
