package backend.security.oauth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GoogleDiscoveryIssuersTest {

    // ── getIssuers(null transport) ─────────────────────────────────────────────

    @Test
    void nullTransport_returnsFallbackIssuers() {
        List<String> issuers = GoogleDiscoveryIssuers.getIssuers(null);
        assertFalse(issuers.isEmpty());
        assertTrue(issuers.stream().anyMatch(s -> s.contains("accounts.google.com")));
    }

    // ── parseIssuersFromDiscovery ─────────────────────────────────────────────

    @Test
    void nullJson_returnsFallback() {
        List<String> issuers = GoogleDiscoveryIssuers.parseIssuersFromDiscovery(null);
        assertFalse(issuers.isEmpty());
        assertTrue(issuers.stream().anyMatch(s -> s.contains("accounts.google.com")));
    }

    @Test
    void blankJson_returnsFallback() {
        List<String> issuers = GoogleDiscoveryIssuers.parseIssuersFromDiscovery("  ");
        assertFalse(issuers.isEmpty());
        assertTrue(issuers.stream().anyMatch(s -> s.contains("accounts.google.com")));
    }

    @Test
    void validJsonWithIssuer_parsedCorrectly() {
        String json = "{\"issuer\":\"https://accounts.google.com\",\"other\":\"value\"}";
        List<String> issuers = GoogleDiscoveryIssuers.parseIssuersFromDiscovery(json);
        assertTrue(issuers.contains("https://accounts.google.com"));
        // legacy form without scheme should also be included
        assertTrue(issuers.contains("accounts.google.com"));
    }

    @Test
    void validJsonNoIssuerField_returnsFallback() {
        String json = "{\"authorization_endpoint\":\"https://accounts.google.com/o/oauth2/auth\"}";
        List<String> issuers = GoogleDiscoveryIssuers.parseIssuersFromDiscovery(json);
        assertFalse(issuers.isEmpty());
        assertTrue(issuers.stream().anyMatch(s -> s.contains("accounts.google.com")));
    }

    @Test
    void malformedJson_returnsFallback() {
        List<String> issuers = GoogleDiscoveryIssuers.parseIssuersFromDiscovery("{not valid json");
        assertFalse(issuers.isEmpty());
        assertTrue(issuers.stream().anyMatch(s -> s.contains("accounts.google.com")));
    }

    @Test
    void issuerWithHttp_legacyFormAdded() {
        String json = "{\"issuer\":\"http://accounts.example.com\"}";
        List<String> issuers = GoogleDiscoveryIssuers.parseIssuersFromDiscovery(json);
        assertTrue(issuers.contains("http://accounts.example.com"));
        assertTrue(issuers.contains("accounts.example.com"));
    }

    @Test
    void emptyIssuerValue_returnsFallback() {
        String json = "{\"issuer\":\"\"}";
        List<String> issuers = GoogleDiscoveryIssuers.parseIssuersFromDiscovery(json);
        assertFalse(issuers.isEmpty());
        assertTrue(issuers.stream().anyMatch(s -> s.contains("accounts.google.com")));
    }
}
