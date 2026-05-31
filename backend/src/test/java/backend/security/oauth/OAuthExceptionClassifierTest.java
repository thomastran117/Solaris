package backend.security.oauth;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import java.io.IOException;
import java.security.SignatureException;

import static org.junit.jupiter.api.Assertions.*;

class OAuthExceptionClassifierTest {

    // ─── toRethrowOrWrap ─────────────────────────────────────────────────────

    @Test
    void toRethrowOrWrap_null_returnsOAuthVerificationError() {
        Throwable result = OAuthExceptionClassifier.toRethrowOrWrap(null);
        assertInstanceOf(OAuthVerificationError.class, result);
    }

    @Test
    void toRethrowOrWrap_error_returnsUnwrapped() {
        Error e = new OutOfMemoryError("oom");
        assertSame(e, OAuthExceptionClassifier.toRethrowOrWrap(e));
    }

    @Test
    void toRethrowOrWrap_invalidOAuthTokenException_returnsUnwrapped() {
        InvalidOAuthTokenException ex = new InvalidOAuthTokenException("bad token");
        assertSame(ex, OAuthExceptionClassifier.toRethrowOrWrap(ex));
    }

    @Test
    void toRethrowOrWrap_oAuthProviderNotConfigured_returnsUnwrapped() {
        OAuthProviderNotConfiguredException ex = new OAuthProviderNotConfiguredException("no config");
        assertSame(ex, OAuthExceptionClassifier.toRethrowOrWrap(ex));
    }

    @Test
    void toRethrowOrWrap_oAuthProviderTransient_returnsUnwrapped() {
        OAuthProviderTransientException ex = new OAuthProviderTransientException("transient");
        assertSame(ex, OAuthExceptionClassifier.toRethrowOrWrap(ex));
    }

    @Test
    void toRethrowOrWrap_oAuthVerificationError_returnsUnwrapped() {
        OAuthVerificationError ex = new OAuthVerificationError("failed", new RuntimeException());
        assertSame(ex, OAuthExceptionClassifier.toRethrowOrWrap(ex));
    }

    @Test
    void toRethrowOrWrap_callNotPermitted_returnsUnwrapped() {
        CallNotPermittedException ex = mock_callNotPermitted();
        assertSame(ex, OAuthExceptionClassifier.toRethrowOrWrap(ex));
    }

    @Test
    void toRethrowOrWrap_genericException_wrapsInOAuthVerificationError() {
        RuntimeException cause = new RuntimeException("something went wrong");
        Throwable result = OAuthExceptionClassifier.toRethrowOrWrap(cause);
        assertInstanceOf(OAuthVerificationError.class, result);
        assertSame(cause, result.getCause());
    }

    @Test
    void toRethrowOrWrap_ioException_wrapsInOAuthVerificationError() {
        IOException cause = new IOException("network timeout");
        Throwable result = OAuthExceptionClassifier.toRethrowOrWrap(cause);
        assertInstanceOf(OAuthVerificationError.class, result);
    }

    // ─── isValidationFailure ─────────────────────────────────────────────────

    @Test
    void isValidationFailure_illegalArgumentException_returnsTrue() {
        assertTrue(OAuthExceptionClassifier.isValidationFailure(
                new IllegalArgumentException("bad arg")));
    }

    @Test
    void isValidationFailure_jwtException_returnsTrue() {
        assertTrue(OAuthExceptionClassifier.isValidationFailure(
                new JwtException("expired")));
    }

    @Test
    void isValidationFailure_signatureException_returnsTrue() {
        assertTrue(OAuthExceptionClassifier.isValidationFailure(
                new SignatureException("invalid sig")));
    }

    @Test
    void isValidationFailure_illegalStateException_returnsTrue() {
        assertTrue(OAuthExceptionClassifier.isValidationFailure(
                new IllegalStateException("bad state")));
    }

    @Test
    void isValidationFailure_wrappedInCauseChain_returnsTrue() {
        RuntimeException wrapper = new RuntimeException("outer",
                new IllegalArgumentException("inner"));
        assertTrue(OAuthExceptionClassifier.isValidationFailure(wrapper));
    }

    @Test
    void isValidationFailure_genericRuntimeException_returnsFalse() {
        assertFalse(OAuthExceptionClassifier.isValidationFailure(
                new RuntimeException("unrelated")));
    }

    // ─── isRetryable ─────────────────────────────────────────────────────────

    @Test
    void isRetryable_ioException_returnsTrue() {
        assertTrue(OAuthExceptionClassifier.isRetryable(new IOException("timeout")));
    }

    @Test
    void isRetryable_oAuthProviderTransient_returnsTrue() {
        assertTrue(OAuthExceptionClassifier.isRetryable(
                new OAuthProviderTransientException("transient")));
    }

    @Test
    void isRetryable_invalidOAuthToken_returnsFalse() {
        assertFalse(OAuthExceptionClassifier.isRetryable(
                new InvalidOAuthTokenException("bad token")));
    }

    @Test
    void isRetryable_illegalArgument_returnsFalse() {
        assertFalse(OAuthExceptionClassifier.isRetryable(
                new IllegalArgumentException("bad arg")));
    }

    @Test
    void isRetryable_wrappedTransientInOAuthVerificationError_returnsTrue() {
        // OAuthVerificationError wrapping an IOException: throwableForClassification
        // unwraps it → IOException is retryable
        OAuthVerificationError wrapped = new OAuthVerificationError("failed",
                new IOException("network error"));
        assertTrue(OAuthExceptionClassifier.isRetryable(wrapped));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /** Constructs a CallNotPermittedException without needing a real CB instance. */
    private static CallNotPermittedException mock_callNotPermitted() {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb =
                io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test");
        // Force open so calling it throws CallNotPermittedException
        cb.transitionToOpenState();
        try {
            cb.executeRunnable(() -> {});
            throw new AssertionError("Expected CallNotPermittedException");
        } catch (CallNotPermittedException e) {
            return e;
        }
    }
}
