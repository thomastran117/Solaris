package backend.configurations.resources;

import backend.services.impl.orders.OrderSseRedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class AppSse {

    @Bean
    public RedisMessageListenerContainer orderSseListenerContainer(
            RedisConnectionFactory cf, OrderSseRedisSubscriber sub) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(cf);
        container.addMessageListener(sub, new PatternTopic("order:stream:*"));
        return container;
    }
}
