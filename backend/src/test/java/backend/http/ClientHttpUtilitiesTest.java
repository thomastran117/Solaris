package backend.http;

import com.google.api.client.http.HttpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

class ClientHttpUtilitiesTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ── ClientRequestContext ──────────────────────────────────────────────────

    @Test
    void get_noRequestContext_returnsUnknown() {
        ClientInfo info = ClientRequestContext.get();
        assertEquals(ClientInfo.UNKNOWN, info);
    }

    @Test
    void get_contextWithoutAttribute_returnsUnknown() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        ClientInfo info = ClientRequestContext.get();
        assertEquals(ClientInfo.UNKNOWN, info);
    }

    @Test
    void get_contextWithClientInfoAttribute_returnsIt() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        ClientInfo stored = new ClientInfo("10.0.0.1", DeviceType.DESKTOP, "Chrome", "Windows", "ua");
        req.setAttribute(ClientRequestContext.ATTRIBUTE_KEY, stored);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        ClientInfo result = ClientRequestContext.get();
        assertSame(stored, result);
    }

    @Test
    void get_contextWithWrongAttributeType_returnsUnknown() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(ClientRequestContext.ATTRIBUTE_KEY, "not a ClientInfo");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        ClientInfo result = ClientRequestContext.get();
        assertEquals(ClientInfo.UNKNOWN, result);
    }

    @Test
    void store_setsAttributeOnRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        ClientInfo info = new ClientInfo("1.2.3.4", DeviceType.MOBILE, "Safari", "iOS", "ua");

        ClientRequestContext.store(req, info);

        assertSame(info, req.getAttribute(ClientRequestContext.ATTRIBUTE_KEY));
    }

    @Test
    void clear_removesAttributeFromRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(ClientRequestContext.ATTRIBUTE_KEY, ClientInfo.UNKNOWN);

        ClientRequestContext.clear(req);

        assertNull(req.getAttribute(ClientRequestContext.ATTRIBUTE_KEY));
    }

    // ── ClientInfo ────────────────────────────────────────────────────────────

    @Test
    void clientInfo_unknownConstant_hasExpectedValues() {
        assertEquals("0.0.0.0", ClientInfo.UNKNOWN.ip());
        assertEquals(DeviceType.UNKNOWN, ClientInfo.UNKNOWN.deviceType());
        assertEquals(ClientInfo.UNKNOWN_VALUE, ClientInfo.UNKNOWN.browser());
        assertEquals(ClientInfo.UNKNOWN_VALUE, ClientInfo.UNKNOWN.os());
    }

    // ── OAuthGoogleHttpTransportFactory ──────────────────────────────────────

    @Test
    void build_validTimeouts_returnsNonNullTransport() {
        HttpTransport transport = OAuthGoogleHttpTransportFactory.build(3000, 5000);
        assertNotNull(transport);
    }

    @Test
    void build_negativeTimeouts_doesNotThrow() {
        assertDoesNotThrow(() -> OAuthGoogleHttpTransportFactory.build(-1, -1));
    }

    @Test
    void build_zeroTimeouts_doesNotThrow() {
        assertDoesNotThrow(() -> OAuthGoogleHttpTransportFactory.build(0, 0));
    }

    // ── TimeoutConnectionFactory ──────────────────────────────────────────────

    @Test
    void timeoutFactory_positiveTimes_storedAsIs() {
        TimeoutConnectionFactory factory = new TimeoutConnectionFactory(2000, 5000);
        assertEquals(2000, factory.getConnectTimeoutMs());
        assertEquals(5000, factory.getReadTimeoutMs());
    }

    @Test
    void timeoutFactory_negativeConnect_clampedToZero() {
        TimeoutConnectionFactory factory = new TimeoutConnectionFactory(-1, 1000);
        assertEquals(0, factory.getConnectTimeoutMs());
        assertEquals(1000, factory.getReadTimeoutMs());
    }

    @Test
    void timeoutFactory_negativeRead_clampedToZero() {
        TimeoutConnectionFactory factory = new TimeoutConnectionFactory(500, -100);
        assertEquals(500, factory.getConnectTimeoutMs());
        assertEquals(0, factory.getReadTimeoutMs());
    }

    @Test
    void timeoutFactory_bothZero_storedAsZero() {
        TimeoutConnectionFactory factory = new TimeoutConnectionFactory(0, 0);
        assertEquals(0, factory.getConnectTimeoutMs());
        assertEquals(0, factory.getReadTimeoutMs());
    }
}
