package backend.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

class ClientRequestContextTest {

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void get_noRequestContext_returnsUnknown() {
        // No request context bound
        ClientInfo info = ClientRequestContext.get();
        assertEquals(ClientInfo.UNKNOWN, info);
    }

    @Test
    void get_requestContextWithNoAttribute_returnsUnknown() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertEquals(ClientInfo.UNKNOWN, ClientRequestContext.get());
    }

    @Test
    void storeAndGet_roundtrip() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ClientInfo stored = new ClientInfo("1.2.3.4", DeviceType.DESKTOP, "Chrome", "Windows", "ua-string");
        ClientRequestContext.store(request, stored);
        assertEquals(stored, ClientRequestContext.get());
    }

    @Test
    void clear_removesAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ClientInfo stored = new ClientInfo("1.2.3.4", DeviceType.MOBILE, "Safari", "iOS", "ua");
        ClientRequestContext.store(request, stored);
        ClientRequestContext.clear(request);
        assertEquals(ClientInfo.UNKNOWN, ClientRequestContext.get());
    }

    @Test
    void get_wrongAttributeType_returnsUnknown() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ClientRequestContext.ATTRIBUTE_KEY, "not-a-client-info");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertEquals(ClientInfo.UNKNOWN, ClientRequestContext.get());
    }

    @Test
    void store_doesNotThrowForNullInfo() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ClientRequestContext.store(request, null);
        assertEquals(ClientInfo.UNKNOWN, ClientRequestContext.get());
    }
}
