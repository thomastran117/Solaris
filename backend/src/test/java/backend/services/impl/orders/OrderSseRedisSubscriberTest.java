package backend.services.impl.orders;

import backend.events.order.SseStatusUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.Message;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class OrderSseRedisSubscriberTest {

    private OrderSseService orderSseService;
    private OrderSseRedisSubscriber subscriber;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        orderSseService = mock(OrderSseService.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        subscriber = new OrderSseRedisSubscriber(orderSseService, objectMapper);
    }

    private Message mockMessage(String channel, String body) {
        Message msg = mock(Message.class);
        when(msg.getChannel()).thenReturn(channel.getBytes());
        when(msg.getBody()).thenReturn(body.getBytes());
        return msg;
    }

    @Test
    void onMessage_extractsOrderIdAndDelegatesToBroadcast() throws Exception {
        UUID orderId = UUID.randomUUID();
        SseStatusUpdateEvent event = new SseStatusUpdateEvent(
                UUID.randomUUID(), orderId, "PACKED", null, null,
                null, null, null, Instant.now(), "status_update");
        String json = objectMapper.writeValueAsString(event);

        subscriber.onMessage(mockMessage("order:stream:" + orderId, json), null);

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<SseStatusUpdateEvent> eventCaptor = ArgumentCaptor.forClass(SseStatusUpdateEvent.class);
        verify(orderSseService, times(1)).broadcast(idCaptor.capture(), eventCaptor.capture());
        assertEquals(orderId, idCaptor.getValue());
        assertEquals("PACKED", eventCaptor.getValue().status());
    }

    @Test
    void onMessage_silentlyIgnoresMalformedJson() {
        UUID orderId = UUID.randomUUID();
        subscriber.onMessage(mockMessage("order:stream:" + orderId, "NOT JSON"), null);
        verify(orderSseService, never()).broadcast(any(), any());
    }

    @Test
    void onMessage_silentlyIgnoresBadOrderId() throws Exception {
        SseStatusUpdateEvent event = new SseStatusUpdateEvent(
                UUID.randomUUID(), UUID.randomUUID(), "PACKED", null, null,
                null, null, null, Instant.now(), "status_update");
        String json = objectMapper.writeValueAsString(event);

        subscriber.onMessage(mockMessage("order:stream:not-a-uuid", json), null);
        verify(orderSseService, never()).broadcast(any(), any());
    }
}
