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

    /**
     * Throws {@link backend.exceptions.http.TooManyRequestException} if the email is
     * currently under a credential-failure lockout. Fail-open on Redis errors.
     */
    void enforceLoginLockout(String email);

    /**
     * Records one credential failure for {@code email}. Once the failure count reaches
     * {@code threshold} within {@code windowSeconds}, a lockout key valid for
     * {@code lockoutDurationSeconds} is set. Fail-open on Redis errors.
     */
    void recordLoginFailure(String email, int threshold, int windowSeconds, int lockoutDurationSeconds);

    /**
     * Clears the failure counter and any active lockout for {@code email}. Call on
     * successful login so a legitimate user who previously fat-fingered their password
     * is not stuck waiting out a lockout. Fail-open on Redis errors.
     */
    void clearLoginFailures(String email);
}
