package backend.annotations.retry;

import org.springframework.core.annotation.AliasFor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Retries a method up to three times when an optimistic-lock or other JPA concurrency
 * failure is thrown ({@link ConcurrencyFailureException} is the Spring superclass of
 * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}). Useful on
 * @Transactional methods that load-then-save entities with a {@code @Version} column
 * and may race against concurrent writers (status transitions, refunds, redemption
 * counters).
 *
 * <p>The annotation is applied around @Transactional, so each retry runs in a fresh
 * transaction that re-reads the entity at its latest version.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Retryable(
        retryFor = ConcurrencyFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2.0, maxDelay = 500))
public @interface RetryOnConcurrency {

    @AliasFor(annotation = Retryable.class, attribute = "maxAttempts")
    int maxAttempts() default 3;
}
