package backend.aspects;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepositoryRetryAspectTest {

    private ProceedingJoinPoint joinPoint;
    private MethodSignature signature;

    @BeforeEach
    void setUp() throws Throwable {
        joinPoint = mock(ProceedingJoinPoint.class);
        signature  = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.proceed()).thenReturn("result");
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    // ─── transaction-active bypass ────────────────────────────────────────────

    @Test
    void aroundRepository_activeTransaction_proceedsDirectly() throws Throwable {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        RepositoryRetryAspect aspect = singleAttemptAspect();

        Object result = aspect.aroundRepository(joinPoint);

        assertEquals("result", result);
        verify(joinPoint).proceed();
    }

    // ─── write-method bypass ──────────────────────────────────────────────────

    @Test
    void aroundRepository_saveMethod_proceedsDirectly() throws Throwable {
        stubMethodName("save");
        RepositoryRetryAspect aspect = singleAttemptAspect();

        assertEquals("result", aspect.aroundRepository(joinPoint));
        verify(joinPoint).proceed();
    }

    @Test
    void aroundRepository_deleteMethod_proceedsDirectly() throws Throwable {
        stubMethodName("delete");
        assertEquals("result", singleAttemptAspect().aroundRepository(joinPoint));
    }

    @Test
    void aroundRepository_deleteAllByIdMethod_proceedsDirectly() throws Throwable {
        stubMethodName("deleteAllById");
        assertEquals("result", singleAttemptAspect().aroundRepository(joinPoint));
    }

    @Test
    void aroundRepository_flushMethod_proceedsDirectly() throws Throwable {
        stubMethodName("flush");
        assertEquals("result", singleAttemptAspect().aroundRepository(joinPoint));
    }

    @Test
    void aroundRepository_saveAllMethod_proceedsDirectly() throws Throwable {
        stubMethodName("saveAll");
        assertEquals("result", singleAttemptAspect().aroundRepository(joinPoint));
    }

    // ─── read-method retry path ───────────────────────────────────────────────

    @Test
    void aroundRepository_readMethod_returnsCorrectResult() throws Throwable {
        stubMethodName("findById");
        assertEquals("result", singleAttemptAspect().aroundRepository(joinPoint));
        verify(joinPoint).proceed();
    }

    @Test
    void aroundRepository_customReadMethod_returnsCorrectResult() throws Throwable {
        stubMethodName("findByEmailAndStatus");
        assertEquals("result", singleAttemptAspect().aroundRepository(joinPoint));
        verify(joinPoint).proceed();
    }

    @Test
    void aroundRepository_readMethod_retriesOnTransientFailureThenSucceeds() throws Throwable {
        stubMethodName("findAll");
        // First call throws a transient error, second call succeeds
        when(joinPoint.proceed())
                .thenThrow(new RuntimeException("transient DB blip"))
                .thenReturn("result");
        // Configure template with 2 attempts so it actually retries
        RepositoryRetryAspect aspect = aspectWithAttempts(2);

        Object result = aspect.aroundRepository(joinPoint);

        assertEquals("result", result);
        verify(joinPoint, times(2)).proceed();
    }

    // ─── exception propagation ────────────────────────────────────────────────

    @Test
    void aroundRepository_readMethod_allAttemptsExhausted_propagatesException() throws Throwable {
        stubMethodName("findAll");
        RuntimeException ex = new RuntimeException("DB down");
        when(joinPoint.proceed()).thenThrow(ex);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> singleAttemptAspect().aroundRepository(joinPoint));
        assertSame(ex, thrown);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /** Single-attempt template: never retries, proceeds immediately on first call. */
    private RepositoryRetryAspect singleAttemptAspect() {
        return aspectWithAttempts(1);
    }

    private RepositoryRetryAspect aspectWithAttempts(int maxAttempts) {
        RetryTemplate template = RetryTemplate.builder()
                .maxAttempts(maxAttempts)
                .noBackoff()
                .retryOn(RuntimeException.class)
                .build();
        return new RepositoryRetryAspect(template);
    }

    private void stubMethodName(String name) {
        Method m = mock(Method.class);
        when(m.getName()).thenReturn(name);
        when(signature.getMethod()).thenReturn(m);
    }
}
