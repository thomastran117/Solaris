package backend.services.impl.orders;

import backend.events.order.SseStatusUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrderSseRedisSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(OrderSseRedisSubscriber.class);
    private static final String CHANNEL_PREFIX = "order:stream:";

    private final OrderSseService orderSseService;
    private final ObjectMapper objectMapper;

    public OrderSseRedisSubscriber(OrderSseService orderSseService, ObjectMapper objectMapper) {
        this.orderSseService = orderSseService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            if (!channel.startsWith(CHANNEL_PREFIX)) return;

            UUID orderId = UUID.fromString(channel.substring(CHANNEL_PREFIX.length()));
            SseStatusUpdateEvent event = objectMapper.readValue(message.getBody(), SseStatusUpdateEvent.class);
            orderSseService.broadcast(orderId, event);
        } catch (Exception e) {
            log.warn("[SSE] Failed to process Redis pub/sub message: {}", e.getMessage());
        }
    }
}
