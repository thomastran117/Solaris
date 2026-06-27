package backend.configurations.application;

import backend.dtos.responses.general.ApiError;
import backend.dtos.responses.general.ApiResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.RiskStepUpRequiredException;
import backend.security.oauth.InvalidOAuthTokenException;
import backend.security.oauth.OAuthProviderTransientException;
import backend.security.oauth.OAuthVerificationError;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Returns a consistent API contract: all error responses use {@link ApiResponse}
 * (success=false with {@link ApiError} payload) as JSON.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String fieldName = err instanceof FieldError fe ? fe.getField() : err.getObjectName();
            String message = err.getDefaultMessage();
            @SuppressWarnings("unchecked")
            List<String> messages = (List<String>) details.computeIfAbsent(fieldName, k -> new ArrayList<String>());
            messages.add(message);
        });
        return build(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_ERROR", details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_ERROR", null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_ERROR", null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoHandlerFoundException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("path", ex.getRequestURL());
        details.put("method", ex.getHttpMethod());
        return build(HttpStatus.NOT_FOUND, "Not found", "NOT_FOUND", details);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        var methods = ex.getSupportedHttpMethods();
        var supported = methods == null
                ? List.<String>of()
                : methods.stream().map(HttpMethod::name).toList();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("allowedMethods", supported);
        return build(
                HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP " + ex.getMethod() + " is not allowed",
                "METHOD_NOT_ALLOWED",
                details
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST, "Missing required parameter: " + ex.getParameterName(), "MISSING_PARAM", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        // A path/query param could not be bound to its target type (e.g. an unknown enum value
        // or a malformed UUID). This is bad client input, not a server fault — return 400, not 500.
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter: " + ex.getName(), "INVALID_PARAM", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean unauthenticated = auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken;
        if (unauthenticated) {
            return build(HttpStatus.UNAUTHORIZED, "Authentication required", "UNAUTHORIZED", null);
        }
        String message = ex.getMessage() != null ? ex.getMessage() : "You do not have permission to access this resource.";
        return build(HttpStatus.FORBIDDEN, message, "FORBIDDEN", null);
    }

    @ExceptionHandler(InvalidOAuthTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidOAuthToken(InvalidOAuthTokenException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid or expired token", "INVALID_TOKEN", null);
    }

    @ExceptionHandler(OAuthProviderTransientException.class)
    public ResponseEntity<ApiResponse<Void>> handleOAuthProviderTransient(OAuthProviderTransientException ex) {
        log.warn("OAuth provider transient failure: {}", ex.getMessage());
        return build(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OAuth provider temporarily unavailable. Please try again.",
                "OAUTH_PROVIDER_UNAVAILABLE",
                null
        );
    }

    @ExceptionHandler(OAuthVerificationError.class)
    public ResponseEntity<ApiResponse<Void>> handleOAuthVerificationError(OAuthVerificationError ex) {
        // Do not log ex, ex.getMessage(), or ex.getCause() — they may contain tokens or provider details
        log.error("OAuth verification failed (details redacted to avoid leakage)");
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", "INTERNAL_ERROR", null);
    }

    @ExceptionHandler(RiskStepUpRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleRiskStepUp(RiskStepUpRequiredException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (ex.getOrderId() != null) {
            details.put("orderId", ex.getOrderId());
        }
        details.put("verificationChannel", ex.getVerificationChannel());
        return build(ex.getStatus(), ex.getMessage(), ex.errorCode(), details);
    }

    @ExceptionHandler(AppHttpException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppHttpError(AppHttpException ex) {
        Map<String, Object> details = null;
        if (ex.getDetail() != null) {
            details = new LinkedHashMap<>();
            details.put("detail", ex.getDetail());
        }
        return build(ex.getStatus(), ex.getMessage(), ex.errorCode(), details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", "INTERNAL_ERROR", null);
    }

    private static ResponseEntity<ApiResponse<Void>> build(
            HttpStatus status,
            String message,
            String code,
            Map<String, Object> details
    ) {
        ApiError error = new ApiError(code, details);
        return ResponseEntity.status(status).body(ApiResponse.error(message, error));
    }
}
