package backend.configurations.application;

import backend.exceptions.http.*;
import backend.security.oauth.InvalidOAuthTokenException;
import backend.security.oauth.OAuthProviderTransientException;
import backend.security.oauth.OAuthVerificationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        SecurityContextHolder.clearContext();
    }

    // ── AppHttpException ──────────────────────────────────────────────────────

    @Test
    void handleAppHttpError_badRequest_returns400() {
        ResponseEntity<?> res = handler.handleAppHttpError(new BadRequestException("missing field"));
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertFalse(res.getBody().toString().contains("success=true"));
    }

    @Test
    void handleAppHttpError_notFound_returns404() {
        ResponseEntity<?> res = handler.handleAppHttpError(new ResourceNotFoundException("product"));
        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    @Test
    void handleAppHttpError_conflict_returns409() {
        ResponseEntity<?> res = handler.handleAppHttpError(new ConflictException("dup key"));
        assertEquals(HttpStatus.CONFLICT, res.getStatusCode());
    }

    @Test
    void handleAppHttpError_withDetail_includesDetail() {
        BadRequestException ex = new BadRequestException("Custom override", "extra detail here");
        ResponseEntity<?> res = handler.handleAppHttpError(ex);
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().toString().contains("extra detail here"));
    }

    @Test
    void handleAppHttpError_noDetail_noDetailKey() {
        BadRequestException ex = new BadRequestException();
        ResponseEntity<?> res = handler.handleAppHttpError(ex);
        assertFalse(res.getBody().toString().contains("extra detail"));
    }

    // ── OAuth exceptions ──────────────────────────────────────────────────────

    @Test
    void handleInvalidOAuthToken_returns401() {
        ResponseEntity<?> res = handler.handleInvalidOAuthToken(new InvalidOAuthTokenException("expired"));
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
        assertTrue(res.getBody().toString().contains("INVALID_TOKEN"));
    }

    @Test
    void handleOAuthProviderTransient_returns503() {
        ResponseEntity<?> res = handler.handleOAuthProviderTransient(
                new OAuthProviderTransientException("timeout", null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, res.getStatusCode());
        assertTrue(res.getBody().toString().contains("OAUTH_PROVIDER_UNAVAILABLE"));
    }

    @Test
    void handleOAuthVerificationError_returns500() {
        ResponseEntity<?> res = handler.handleOAuthVerificationError(
                new OAuthVerificationError("error", null));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
    }

    // ── NoHandlerFoundException ───────────────────────────────────────────────

    @Test
    void handleNotFound_returns404WithPath() {
        NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/unknown", null);
        ResponseEntity<?> res = handler.handleNotFound(ex);
        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertTrue(res.getBody().toString().contains("NOT_FOUND"));
    }

    // ── RiskStepUpRequiredException ───────────────────────────────────────────

    @Test
    void handleRiskStepUp_returns428() {
        UUID orderId = UUID.randomUUID();
        RiskStepUpRequiredException ex = new RiskStepUpRequiredException(orderId, "EMAIL");
        ResponseEntity<?> res = handler.handleRiskStepUp(ex);
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, res.getStatusCode());
        assertTrue(res.getBody().toString().contains("orderId"));
        assertTrue(res.getBody().toString().contains("EMAIL"));
    }

    @Test
    void handleRiskStepUp_nullOrderId_omitsOrderId() {
        RiskStepUpRequiredException ex = new RiskStepUpRequiredException(null);
        ResponseEntity<?> res = handler.handleRiskStepUp(ex);
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, res.getStatusCode());
    }

    // ── AccessDeniedException ─────────────────────────────────────────────────

    @Test
    void handleAccessDenied_noAuth_returns401() {
        // SecurityContextHolder cleared in setUp → auth = null → UNAUTHORIZED
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("denied");
        ResponseEntity<?> res = handler.handleAccessDenied(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }

    // ── Unknown exception ─────────────────────────────────────────────────────

    @Test
    void handleUnknown_returns500() {
        ResponseEntity<?> res = handler.handleUnknown(new RuntimeException("boom"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
        assertTrue(res.getBody().toString().contains("INTERNAL_ERROR"));
    }

    // ── MissingServletRequestParameterException ───────────────────────────────

    @Test
    void handleMissingParam_returns400WithParamName() throws Exception {
        org.springframework.web.bind.MissingServletRequestParameterException ex =
                new org.springframework.web.bind.MissingServletRequestParameterException("userId", "UUID");
        ResponseEntity<?> res = handler.handleMissingParam(ex);
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().toString().contains("userId"));
    }
}
