package backend.security.oauth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OAuthVerificationErrorTest {

    // ─── Constructor 1: Error wrapping ───────────────────────────────────────

    @Test
    void errorConstructor_nullError_usesDefaultMessage() {
        OAuthVerificationError ex = new OAuthVerificationError((Error) null);
        assertEquals("OAuth verification failed (JVM error)", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void errorConstructor_nonNullError_usesErrorMessage() {
        Error cause = new Error("JVM stack overflow");
        OAuthVerificationError ex = new OAuthVerificationError(cause);
        assertEquals("JVM stack overflow", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    // ─── Constructor 2: message + Throwable ──────────────────────────────────

    @Test
    void messageConstructor_nullMessageWithCause_usesCauseMessage() {
        RuntimeException cause = new RuntimeException("root cause");
        OAuthVerificationError ex = new OAuthVerificationError(null, cause);
        assertEquals("root cause", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void messageConstructor_nullMessageNullCause_usesDefaultMessage() {
        OAuthVerificationError ex = new OAuthVerificationError(null, null);
        assertEquals("OAuth verification failed", ex.getMessage());
        assertNull(ex.getCause());
    }
}
