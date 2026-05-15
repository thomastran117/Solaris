package backend.services.intf;

import java.util.Optional;

/**
 * Per-user idempotency cache for write endpoints (order creation, refunds, etc.).
 * Clients send an {@code Idempotency-Key} header; we map {@code (userId, key)} to the
 * id of the resource that was created on the first successful call, so retries do not
 * create duplicates.
 *
 * <p>Storage is Redis-backed with a 24h TTL by default — long enough to cover client
 * retry windows but short enough that keys can be reused after the window closes.
 */
public interface IdempotencyService {

    /**
     * Returns the previously-stored result id for {@code (userId, key)} if one exists.
     */
    Optional<Long> lookup(String scope, long userId, String key);

    /**
     * Stores the result id of a successful operation. Subsequent calls to
     * {@link #lookup} with the same {@code (scope, userId, key)} will return it.
     */
    void store(String scope, long userId, String key, long resultId);

    /**
     * Atomically claims the key for in-flight processing. Returns {@code true} if this
     * caller is the first to claim — proceed with the operation. Returns {@code false}
     * if another caller is concurrently processing the same key — the duplicate
     * should be rejected so the in-flight operation completes once.
     *
     * <p>The claim is held only briefly; the final {@link #store} call replaces it with
     * the result id.
     */
    boolean claim(String scope, long userId, String key, long claimTtlSeconds);
}
