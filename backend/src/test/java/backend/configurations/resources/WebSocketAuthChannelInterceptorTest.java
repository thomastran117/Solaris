package backend.configurations.resources;

import backend.services.intf.auth.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketAuthChannelInterceptorTest {

    private TokenService tokenService;
    private WebSocketAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tokenService = mock(TokenService.class);
        interceptor = new WebSocketAuthChannelInterceptor(tokenService);
    }

    private Message<byte[]> buildConnect(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authHeader != null) {
            accessor.setNativeHeader("Authorization", authHeader);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> buildSend() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/delivery/test/location");
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void preSend_validToken_setsUserOnAccessor() {
        UUID userId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null);
        when(tokenService.getAuthentication("valid.token")).thenReturn(auth);

        Message<?> result = interceptor.preSend(buildConnect("Bearer valid.token"), null);

        assertNotNull(result);
        StompHeaderAccessor out = StompHeaderAccessor.wrap(result);
        assertNotNull(out.getUser());
        assertEquals(userId.toString(), out.getUser().getName());
    }

    @Test
    void preSend_missingAuthHeader_throwsMessageDeliveryException() {
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(buildConnect(null), null));
    }

    @Test
    void preSend_invalidToken_throwsMessageDeliveryException() {
        when(tokenService.getAuthentication(anyString()))
                .thenThrow(new RuntimeException("bad token"));
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(buildConnect("Bearer bad.token"), null));
    }

    @Test
    void preSend_nonConnectFrame_passesThrough() {
        Message<byte[]> send = buildSend();
        Message<?> result = interceptor.preSend(send, null);
        assertEquals(send, result);
        verify(tokenService, never()).getAuthentication(anyString());
    }
}
