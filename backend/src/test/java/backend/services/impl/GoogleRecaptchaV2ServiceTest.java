package backend.services.impl;

import backend.configurations.environment.EnvironmentSetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class GoogleRecaptchaV2ServiceTest {

    private RestTemplate restTemplate;
    private RetryTemplate retryTemplate;
    private GoogleRecaptchaV2Service service;

    @BeforeEach
    void setUp() {
        restTemplate  = mock(RestTemplate.class);
        retryTemplate = mock(RetryTemplate.class);

        EnvironmentSetting.Security security = mock(EnvironmentSetting.Security.class);
        when(security.getRecaptchaSecretKey()).thenReturn("test-secret-key");
        EnvironmentSetting env = mock(EnvironmentSetting.class);
        when(env.getSecurity()).thenReturn(security);

        service = new GoogleRecaptchaV2Service(env, restTemplate, retryTemplate);
    }

    // ─── input validation ────────────────────────────────────────────────────

    @Test
    void verify_nullToken_returnsFalse() {
        assertFalse(service.verify(null));
        verifyNoInteractions(retryTemplate);
    }

    @Test
    void verify_blankToken_returnsFalse() {
        assertFalse(service.verify("   "));
        verifyNoInteractions(retryTemplate);
    }

    @Test
    void verify_emptyToken_returnsFalse() {
        assertFalse(service.verify(""));
        verifyNoInteractions(retryTemplate);
    }

    // ─── empty secret key ─────────────────────────────────────────────────────

    @Test
    void verify_emptySecretKey_returnsFalse() {
        EnvironmentSetting.Security security = mock(EnvironmentSetting.Security.class);
        when(security.getRecaptchaSecretKey()).thenReturn("");
        EnvironmentSetting env = mock(EnvironmentSetting.class);
        when(env.getSecurity()).thenReturn(security);
        GoogleRecaptchaV2Service svc = new GoogleRecaptchaV2Service(env, restTemplate, retryTemplate);

        assertFalse(svc.verify("valid-token"));
        verifyNoInteractions(retryTemplate);
    }

    // ─── verify delegates to RetryTemplate ───────────────────────────────────

    @Test
    void verify_successfulVerification_returnsTrue() throws Throwable {
        when(retryTemplate.execute(any(RetryCallback.class))).thenReturn(Boolean.TRUE);
        assertTrue(service.verify("valid-token"));
    }

    @Test
    void verify_failedVerification_returnsFalse() throws Throwable {
        when(retryTemplate.execute(any(RetryCallback.class))).thenReturn(Boolean.FALSE);
        assertFalse(service.verify("valid-token"));
    }

    @Test
    void verify_nullResponseFromRetry_returnsFalse() throws Throwable {
        when(retryTemplate.execute(any(RetryCallback.class))).thenReturn(null);
        assertFalse(service.verify("valid-token"));
    }

    // ─── fail closed on exception ─────────────────────────────────────────────

    @Test
    void verify_retriesExhausted_returnsFalse() throws Throwable {
        when(retryTemplate.execute(any(RetryCallback.class)))
                .thenThrow(new RuntimeException("network error after retries"));
        assertFalse(service.verify("valid-token"));
    }

    @Test
    void verify_unexpectedException_returnsFalse() throws Throwable {
        when(retryTemplate.execute(any(RetryCallback.class)))
                .thenThrow(new RuntimeException("unexpected"));
        assertFalse(service.verify("valid-token"));
    }

    // ─── verify with remoteIp overload ────────────────────────────────────────

    @Test
    void verify_withRemoteIp_delegatesToRetry() throws Throwable {
        when(retryTemplate.execute(any(RetryCallback.class))).thenReturn(Boolean.TRUE);
        assertTrue(service.verify("valid-token", "1.2.3.4"));
        verify(retryTemplate).execute(any(RetryCallback.class));
    }

    @Test
    void verify_withNullRemoteIp_doesNotFail() throws Throwable {
        when(retryTemplate.execute(any(RetryCallback.class))).thenReturn(Boolean.TRUE);
        assertTrue(service.verify("valid-token", null));
    }

    @Test
    void verify_withBlankRemoteIp_doesNotFail() throws Throwable {
        when(retryTemplate.execute(any(RetryCallback.class))).thenReturn(Boolean.TRUE);
        assertTrue(service.verify("valid-token", "  "));
    }
}
