package backend.security.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class OAuthRetryableTest {

    // ─── isRetryable — transient types ───────────────────────────────────────

    @Test
    void isRetryable_ioException_returnsTrue() {
        assertTrue(OAuthRetryable.isRetryable(new IOException("connection reset")));
    }

    @Test
    void isRetryable_resourceAccessException_returnsTrue() {
        assertTrue(OAuthRetryable.isRetryable(new ResourceAccessException("timeout")));
    }

    @Test
    void isRetryable_oauthProviderTransientException_returnsTrue() {
        assertTrue(OAuthRetryable.isRetryable(new OAuthProviderTransientException("provider slow")));
    }

    @Test
    void isRetryable_http5xx_returnsTrue() {
        assertTrue(OAuthRetryable.isRetryable(
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Error", null, null, null)));
    }

    @Test
    void isRetryable_http4xx_returnsFalse() {
        // 4xx is not retryable — token invalid, not a transient failure
        assertFalse(OAuthRetryable.isRetryable(
                HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null)));
    }

    // ─── isRetryable — non-transient types ───────────────────────────────────

    @Test
    void isRetryable_invalidOAuthTokenException_returnsFalse() {
        assertFalse(OAuthRetryable.isRetryable(new InvalidOAuthTokenException("bad token")));
    }

    @Test
    void isRetryable_runtimeException_returnsFalse() {
        assertFalse(OAuthRetryable.isRetryable(new RuntimeException("generic error")));
    }

    @Test
    void isRetryable_null_returnsFalse() {
        assertFalse(OAuthRetryable.isRetryable(null));
    }

    // ─── isRetryable — cause chain ────────────────────────────────────────────

    @Test
    void isRetryable_wrappedIoException_returnsTrue() {
        RuntimeException wrapper = new RuntimeException("outer",
                new IOException("inner network failure"));
        assertTrue(OAuthRetryable.isRetryable(wrapper));
    }

    // ─── throwableForClassification ──────────────────────────────────────────

    @Test
    void throwableForClassification_oauthVerificationError_returnsCause() {
        IOException cause = new IOException("network error");
        OAuthVerificationError wrapped = new OAuthVerificationError("failed", cause);

        assertSame(cause, OAuthRetryable.throwableForClassification(wrapped));
    }

    @Test
    void throwableForClassification_oauthVerificationErrorNoCause_returnsSelf() {
        OAuthVerificationError err = new OAuthVerificationError("failed", null);
        // cause is null → return self
        assertSame(err, OAuthRetryable.throwableForClassification(err));
    }

    @Test
    void throwableForClassification_regularException_returnsSelf() {
        RuntimeException ex = new RuntimeException("regular");
        assertSame(ex, OAuthRetryable.throwableForClassification(ex));
    }

    @Test
    void throwableForClassification_null_returnsNull() {
        assertNull(OAuthRetryable.throwableForClassification(null));
    }

    // ─── getTransientTypes ────────────────────────────────────────────────────

    @Test
    void getTransientTypes_isImmutableCopy() {
        var types = OAuthRetryable.getTransientTypes();
        assertThrows(UnsupportedOperationException.class,
                () -> types.add(RuntimeException.class));
    }

    @Test
    void getTransientTypes_containsExpectedTypes() {
        var types = OAuthRetryable.getTransientTypes();
        assertTrue(types.contains(OAuthProviderTransientException.class));
        assertTrue(types.contains(IOException.class));
        assertTrue(types.contains(ResourceAccessException.class));
    }
}
