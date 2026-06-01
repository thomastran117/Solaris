package backend.services.impl;

import backend.services.intf.AuthAuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AuthAuditLoggerImplTest {

    private AuthAuditLoggerImpl logger;

    @BeforeEach
    void setUp() {
        logger = new AuthAuditLoggerImpl();
    }

    @Test
    void log_noHttpContext_doesNotThrow() {
        // ClientRequestContext.get() returns null outside a request → "unknown" ip
        assertDoesNotThrow(() -> logger.log(AuthAuditLogger.Event.LOGIN_SUCCESS, "user-123", "device=mobile"));
    }

    @Test
    void log_nullSubjectAndNullDetail_doesNotThrow() {
        assertDoesNotThrow(() -> logger.log(AuthAuditLogger.Event.LOGIN_FAILURE, null, null));
    }

    @Test
    void log_detailWithNewline_doesNotThrow() {
        // Sanitize strips newlines — the key invariant is no exception is thrown
        assertDoesNotThrow(() -> logger.log(
                AuthAuditLogger.Event.REFUND_ISSUED,
                "staff-1",
                "line1\ninjected log line"));
    }

    @Test
    void log_allEventTypes_doNotThrow() {
        for (AuthAuditLogger.Event event : AuthAuditLogger.Event.values()) {
            assertDoesNotThrow(() -> logger.log(event, "subject", "detail"),
                    "Event " + event + " must not throw");
        }
    }
}
