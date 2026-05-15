package backend.services.intf;

/**
 * Fixed-window rate limiter backed by Redis (INCR + EXPIRE). Each call to
 * {@link #enforce(String, String, int, int)} increments a per-bucket counter; once the
 * counter exceeds {@code limit} within {@code windowSeconds}, the request is rejected
 * with {@link backend.exceptions.http.TooManyRequestException}.
 *
 * <p>Buckets are identified by a {@code scope} (e.g. {@code "auth:login:ip"}) and a
 * {@code subject} (e.g. the client IP or hashed email). The combination keeps unrelated
 * limits from interfering with each other.
 */
public interface RateLimitService {

    /**
     * Counts a hit against {@code (scope, subject)} and throws
     * {@link backend.exceptions.http.TooManyRequestException} if the per-window limit
     * has been exceeded. Fail-open on Redis errors — a degraded cache must not lock
     * legitimate users out of authentication.
     *
     * @param scope          a stable namespace identifying the protected operation
     * @param subject        the dimension being limited (IP, email, account id)
     * @param limit          maximum number of hits allowed within the window
     * @param windowSeconds  the window size in seconds (TTL applied on first hit)
     */
    void enforce(String scope, String subject, int limit, int windowSeconds);
}
