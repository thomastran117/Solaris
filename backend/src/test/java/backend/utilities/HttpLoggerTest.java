package backend.utilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class HttpLoggerTest {

    // ── isIgnored paths ───────────────────────────────────────────────────────

    @Test
    void log_ignoredPath_doesNotThrow() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/error");
        assertDoesNotThrow(() -> HttpLogger.log(req, 200, 50));
    }

    @Test
    void log_ignoredApiError_doesNotThrow() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/error");
        assertDoesNotThrow(() -> HttpLogger.log(req, 500, 10));
    }

    @Test
    void log_ignoredApiErrorDisabled_doesNotThrow() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/error-disabled");
        assertDoesNotThrow(() -> HttpLogger.log(req, 404, 5));
    }

    // ── status code colour branches ───────────────────────────────────────────

    @ParameterizedTest(name = "status={0}")
    @CsvSource({ "200", "201", "204", "299" })
    void log_2xxStatus_doesNotThrow(int status) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");
        assertDoesNotThrow(() -> HttpLogger.log(req, status, 100));
    }

    @ParameterizedTest(name = "status={0}")
    @CsvSource({ "301", "302", "304", "399" })
    void log_3xxStatus_doesNotThrow(int status) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders");
        assertDoesNotThrow(() -> HttpLogger.log(req, status, 200));
    }

    @ParameterizedTest(name = "status={0}")
    @CsvSource({ "400", "401", "403", "404", "422", "429", "499" })
    void log_4xxStatus_doesNotThrow(int status) {
        MockHttpServletRequest req = new MockHttpServletRequest("PUT", "/api/users/1");
        assertDoesNotThrow(() -> HttpLogger.log(req, status, 30));
    }

    @ParameterizedTest(name = "status={0}")
    @CsvSource({ "500", "502", "503" })
    void log_5xxStatus_doesNotThrow(int status) {
        MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/api/cart/1");
        assertDoesNotThrow(() -> HttpLogger.log(req, status, 1200));
    }

    // ── latency colour branches ───────────────────────────────────────────────

    @Test
    void log_fastLatency_doesNotThrow() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/health");
        assertDoesNotThrow(() -> HttpLogger.log(req, 200, 50));   // < 500ms → green
    }

    @Test
    void log_mediumLatency_doesNotThrow() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/health");
        assertDoesNotThrow(() -> HttpLogger.log(req, 200, 750));  // 500-999ms → yellow
    }

    @Test
    void log_slowLatency_doesNotThrow() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/health");
        assertDoesNotThrow(() -> HttpLogger.log(req, 200, 2000)); // ≥1000ms → red
    }

    // ── query string path ─────────────────────────────────────────────────────

    @Test
    void log_withQueryString_doesNotThrow() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");
        req.setQueryString("page=1&size=20");
        assertDoesNotThrow(() -> HttpLogger.log(req, 200, 80));
    }

    @Test
    void log_blankQueryString_doesNotThrow() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");
        req.setQueryString("   ");
        assertDoesNotThrow(() -> HttpLogger.log(req, 200, 80));
    }
}
