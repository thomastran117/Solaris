package backend.aspects;

import backend.configurations.application.OAuthMetrics;
import backend.security.oauth.InvalidOAuthTokenException;
import backend.security.oauth.OAuthVerificationError;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class OAuthRetryAspectTest {

    private OAuthMetrics oauthMetrics;
    private ProceedingJoinPoint joinPoint;
    private RetryTemplate retryTemplate;
    private CircuitBreaker circuitBreaker;
    private OAuthRetryAspect aspect;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws NoSuchMethodException {
        oauthMetrics   = mock(OAuthMetrics.class);
        joinPoint      = mock(ProceedingJoinPoint.class);

        // Stub signature chain for methodName derivation
        MethodSignature sig = mock(MethodSignature.class);
        Method method = String.class.getDeclaredMethod("length");
        when(sig.getDeclaringType()).thenReturn((Class) String.class);
        when(sig.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(sig);

        retryTemplate  = singleAttemptTemplate();
        circuitBreaker = CircuitBreaker.ofDefaults("test");
        aspect = new OAuthRetryAspect(retryTemplate, circuitBreaker, oauthMetrics);
    }

    // ─── happy path ───────────────────────────────────────────────────────────

    @Test
    void aroundOAuthVerification_success_returnsResult() throws Throwable {
        when(joinPoint.proceed()).thenReturn("oauth-user");

        Object result = aspect.aroundOAuthVerification(joinPoint);

        assertEquals("oauth-user", result);
    }

    @Test
    void aroundOAuthVerification_success_recordsDuration() throws Throwable {
        when(joinPoint.proceed()).thenReturn("oauth-user");

        aspect.aroundOAuthVerification(joinPoint);

        verify(oauthMetrics).recordDuration(anyLong());
    }

    @Test
    void aroundOAuthVerification_withoutMetrics_successDoesNotThrow() throws Throwable {
        OAuthRetryAspect noMetricsAspect = new OAuthRetryAspect(retryTemplate, circuitBreaker, null);
        when(joinPoint.proceed()).thenReturn("ok");

        assertDoesNotThrow(() -> noMetricsAspect.aroundOAuthVerification(joinPoint));
    }

    // ─── retry on second attempt ──────────────────────────────────────────────

    @Test
    void aroundOAuthVerification_secondAttempt_recordsRetryMetric() throws Throwable {
        // Two attempts, retrying on any Exception (OAuthVerificationError extends RuntimeException)
        RetryTemplate twoAttempts = RetryTemplate.builder()
                .maxAttempts(2)
                .noBackoff()
                .retryOn(Exception.class)
                .build();
        CircuitBreaker cb = CircuitBreaker.ofDefaults("retry-test");
        OAuthRetryAspect retryAspect = new OAuthRetryAspect(twoAttempts, cb, oauthMetrics);

        // First call throws (wrapped to OAuthVerificationError by classifier), second succeeds
        when(joinPoint.proceed())
                .thenThrow(new RuntimeException("transient"))
                .thenReturn("ok");

        retryAspect.aroundOAuthVerification(joinPoint);

        verify(oauthMetrics).recordRetry();
    }

    // ─── exception handling ───────────────────────────────────────────────────

    @Test
    void aroundOAuthVerification_callNotPermitted_rethrows() {
        CircuitBreaker openCb = CircuitBreaker.ofDefaults("open-rethrow-test");
        openCb.transitionToOpenState();
        OAuthRetryAspect aspectWithOpenCb = new OAuthRetryAspect(retryTemplate, openCb, oauthMetrics);

        assertThrows(CallNotPermittedException.class,
                () -> aspectWithOpenCb.aroundOAuthVerification(joinPoint));
    }

    @Test
    void aroundOAuthVerification_callNotPermitted_recordsDuration() {
        CircuitBreaker openCb = CircuitBreaker.ofDefaults("open-duration-test");
        openCb.transitionToOpenState();
        OAuthRetryAspect openAspect = new OAuthRetryAspect(retryTemplate, openCb, oauthMetrics);

        assertThrows(CallNotPermittedException.class,
                () -> openAspect.aroundOAuthVerification(joinPoint));

        verify(oauthMetrics).recordDuration(anyLong());
    }

    @Test
    void aroundOAuthVerification_oauthVerificationError_rethrows() throws Throwable {
        OAuthVerificationError err = new OAuthVerificationError("failed", new RuntimeException());
        when(joinPoint.proceed()).thenThrow(err);

        assertThrows(OAuthVerificationError.class,
                () -> aspect.aroundOAuthVerification(joinPoint));
    }

    @Test
    void aroundOAuthVerification_invalidOAuthTokenException_wrapped_rethrowsAsOAuthVerificationError()
            throws Throwable {
        // InvalidOAuthTokenException is a known type — OAuthExceptionClassifier returns it as-is
        when(joinPoint.proceed()).thenThrow(new InvalidOAuthTokenException("bad"));

        assertThrows(InvalidOAuthTokenException.class,
                () -> aspect.aroundOAuthVerification(joinPoint));
    }

    @Test
    void aroundOAuthVerification_jvmError_propagatesUnwrapped() throws Throwable {
        Error err = new OutOfMemoryError("oom");
        when(joinPoint.proceed()).thenThrow(err);

        assertThrows(OutOfMemoryError.class,
                () -> aspect.aroundOAuthVerification(joinPoint));
    }

    @Test
    void aroundOAuthVerification_failure_recordsDuration() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new OAuthVerificationError("fail", new RuntimeException()));

        assertThrows(OAuthVerificationError.class,
                () -> aspect.aroundOAuthVerification(joinPoint));

        verify(oauthMetrics).recordDuration(anyLong());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static RetryTemplate singleAttemptTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(1)
                .noBackoff()
                .build();
    }
}
