package backend.security.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OAuthClaimUtilsTest {

    private static Jwt jwt(String key, Object value) {
        Jwt.Builder b = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        if (value != null) b.claim(key, value);
        return b.build();
    }

    // ── String claim ──────────────────────────────────────────────────────────

    @Test
    void stringClaim_preferredKey_returned() {
        Jwt j = jwt("preferred_username", "alice@example.com");
        assertEquals("alice@example.com", OAuthClaimUtils.getClaim(j, "preferred_username", "email"));
    }

    @Test
    void stringClaim_preferredMissing_fallbackUsed() {
        Jwt j = jwt("email", "alice@example.com");
        assertEquals("alice@example.com", OAuthClaimUtils.getClaim(j, "preferred_username", "email"));
    }

    @Test
    void stringClaim_bothMissing_nullReturned() {
        Jwt j = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("other", "value")
                .build();
        assertNull(OAuthClaimUtils.getClaim(j, "preferred_username", "email"));
    }

    @Test
    void stringClaim_blankValue_treatedAsMissing() {
        Jwt j = jwt("preferred_username", "  ");
        assertNull(OAuthClaimUtils.getClaim(j, "preferred_username", null));
    }

    @Test
    void noFallback_null_noSecondLookup() {
        Jwt j = jwt("email", "alice@example.com");
        assertNull(OAuthClaimUtils.getClaim(j, "preferred_username", null));
    }

    // ── Number claim ─────────────────────────────────────────────────────────

    @Test
    void numberClaim_convertedToString() {
        Jwt j = jwt("count", 42L);
        assertEquals("42", OAuthClaimUtils.getClaim(j, "count", null));
    }

    // ── Boolean claim ─────────────────────────────────────────────────────────

    @Test
    void booleanClaim_convertedToString() {
        Jwt j = jwt("email_verified", true);
        assertEquals("true", OAuthClaimUtils.getClaim(j, "email_verified", null));
    }

    // ── Collection claim ─────────────────────────────────────────────────────

    @Test
    void collectionClaim_firstStringElement_returned() {
        Jwt j = jwt("preferred_username", List.of("alice@example.com", "other@example.com"));
        assertEquals("alice@example.com", OAuthClaimUtils.getClaim(j, "preferred_username", null));
    }

    @Test
    void collectionClaim_emptyCollection_nullReturned() {
        Jwt j = jwt("preferred_username", List.of());
        assertNull(OAuthClaimUtils.getClaim(j, "preferred_username", null));
    }

    @Test
    void collectionClaim_numberElements_toStringUsed() {
        Jwt j = jwt("ids", List.of(1, 2, 3));
        assertEquals("1", OAuthClaimUtils.getClaim(j, "ids", null));
    }

    // ── Map claim ─────────────────────────────────────────────────────────────

    @Test
    void mapClaim_nullReturned() {
        Jwt j = jwt("complex", Map.of("k", "v"));
        assertNull(OAuthClaimUtils.getClaim(j, "complex", null));
    }

    // ── Null claim value ──────────────────────────────────────────────────────

    @Test
    void nullClaim_nullReturned() {
        Jwt j = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("name", "present")
                .build();
        assertNull(OAuthClaimUtils.getClaim(j, "missing_key", null));
    }
}
