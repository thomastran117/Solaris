package backend.configurations.resources;

import backend.configurations.environment.EnvironmentSetting;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class AppWebSocket implements WebSocketMessageBrokerConfigurer {

    private final EnvironmentSetting env;
    private final WebSocketAuthChannelInterceptor authInterceptor;

    public AppWebSocket(EnvironmentSetting env, WebSocketAuthChannelInterceptor authInterceptor) {
        this.env = env;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/user");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        List<String> origins = env.getCors().getAllowedOrigins();
        String[] patterns = origins.isEmpty()
                ? new String[]{"http://localhost:3090"}
                : origins.toArray(new String[0]);
        registry.addEndpoint("/ws/delivery")
                .setAllowedOriginPatterns(patterns);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
