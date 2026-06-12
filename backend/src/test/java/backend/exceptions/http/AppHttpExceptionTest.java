package backend.exceptions.http;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AppHttpExceptionTest {

    // ── errorCode derivation ──────────────────────────────────────────────────

    @Test
    void badRequestException_errorCode() {
        assertEquals("BAD_REQUEST", new BadRequestException().errorCode());
    }

    @Test
    void resourceNotFoundException_errorCode() {
        assertEquals("RESOURCE_NOT_FOUND", new ResourceNotFoundException("test").errorCode());
    }

    @Test
    void conflictException_errorCode() {
        assertEquals("CONFLICT", new ConflictException("msg").errorCode());
    }

    @Test
    void forbiddenException_errorCode() {
        assertEquals("FORBIDDEN", new ForbiddenException().errorCode());
    }

    @Test
    void unauthorizedException_errorCode() {
        assertEquals("UNAUTHORIZED", new UnauthorizedException().errorCode());
    }

    @Test
    void tooManyRequestException_errorCode() {
        assertEquals("TOO_MANY_REQUEST", new TooManyRequestException("too many").errorCode());
    }

    @Test
    void riskStepUpRequiredException_errorCode() {
        assertEquals("RISK_STEP_UP_REQUIRED", new RiskStepUpRequiredException(UUID.randomUUID()).errorCode());
    }

    @Test
    void internalServerErrorException_errorCode() {
        assertEquals("INTERNAL_SERVER_ERROR", new InternalServerErrorException("err").errorCode());
    }

    // ── Status codes ──────────────────────────────────────────────────────────

    @Test
    void badRequestException_status400() {
        assertEquals(HttpStatus.BAD_REQUEST, new BadRequestException().getStatus());
    }

    @Test
    void forbiddenException_status403() {
        assertEquals(HttpStatus.FORBIDDEN, new ForbiddenException().getStatus());
    }

    @Test
    void resourceNotFoundException_status404() {
        assertEquals(HttpStatus.NOT_FOUND, new ResourceNotFoundException("item").getStatus());
    }

    @Test
    void conflictException_status409() {
        assertEquals(HttpStatus.CONFLICT, new ConflictException("conflict").getStatus());
    }

    @Test
    void tooManyRequestException_status429() {
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, new TooManyRequestException("slow down").getStatus());
    }

    @Test
    void riskStepUpRequiredException_status428() {
        assertEquals(HttpStatus.PRECONDITION_REQUIRED,
                new RiskStepUpRequiredException(UUID.randomUUID()).getStatus());
    }

    // ── Message and detail ────────────────────────────────────────────────────

    @Test
    void badRequestException_noArgs_defaultMessage() {
        BadRequestException ex = new BadRequestException();
        assertEquals("Bad request", ex.getMessage());
        assertNull(ex.getDetail());
    }

    @Test
    void badRequestException_withDetail_detailPopulated() {
        BadRequestException ex = new BadRequestException("field is required");
        assertEquals("field is required", ex.getDetail());
        assertEquals("Bad request", ex.getMessage());
    }

    @Test
    void badRequestException_withMessageOverrideAndDetail() {
        BadRequestException ex = new BadRequestException("Custom msg", "extra detail");
        assertEquals("Custom msg", ex.getMessage());
        assertEquals("extra detail", ex.getDetail());
    }

    @Test
    void riskStepUpRequiredException_orderIdAndChannel() {
        UUID orderId = UUID.randomUUID();
        RiskStepUpRequiredException ex = new RiskStepUpRequiredException(orderId, "SMS");
        assertEquals(orderId, ex.getOrderId());
        assertEquals("SMS", ex.getVerificationChannel());
    }

    @Test
    void riskStepUpRequiredException_defaultChannel_email() {
        RiskStepUpRequiredException ex = new RiskStepUpRequiredException(UUID.randomUUID());
        assertEquals("EMAIL", ex.getVerificationChannel());
    }

    @Test
    void riskStepUpRequiredException_nullOrderId_allowed() {
        RiskStepUpRequiredException ex = new RiskStepUpRequiredException(null);
        assertNull(ex.getOrderId());
    }

    // ── Subtypes ──────────────────────────────────────────────────────────────

    @Test
    void goneException_status410() {
        assertEquals(HttpStatus.GONE, new GoneException("removed").getStatus());
    }

    @Test
    void payloadTooLargeException_status413() {
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, new PayloadTooLargeException("file too big").getStatus());
    }

    @Test
    void unprocessableEntityException_status422() {
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, new UnprocessableEntityException("invalid").getStatus());
    }

    @Test
    void serviceUnavailableException_status503() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, new ServiceUnavaliableException("down").getStatus());
    }
}
