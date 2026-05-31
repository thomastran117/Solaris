package backend.services.impl;

import backend.exceptions.http.TooManyRequestException;
import backend.services.intf.CacheService;
import backend.services.intf.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RateLimitServiceImpl implements RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitServiceImpl.class);

    private final CacheService cache;

    public RateLimitServiceImpl(CacheService cache) {
        this.cache = cache;
    }

    private static final String LOCKOUT_COUNT_PREFIX = "lockout:count:";
    private static final String LOCKOUT_UNTIL_PREFIX = "lockout:until:";

    @Override
    public void enforce(String scope, String subject, int limit, int windowSeconds) {
        if (subject == null || subject.isBlank()) {
            // Degenerate input — skip rather than rate-limit every anonymous caller into
            // the same bucket, which would amount to a denial-of-service vector.
            return;
        }
        String key = "ratelimit:" + scope + ":" + subject;
        long count;
        try {
            count = cache.incrementWithTtl(key, windowSeconds);
        } catch (Exception ex) {
            // Fail open: a Redis outage must not lock users out. The CLAUDE.md rule
            // ("a Redis failure must never cause a write path to fail") applies here too.
            log.warn("Rate-limit cache unavailable for scope={} subject={}: {}",
                    scope, subject, ex.getMessage());
            return;
        }
        if (count > limit) {
            throw new TooManyRequestException(
                    "Too many " + scope + " attempts. Please wait and try again.");
        }
    }

    @Override
    public void enforceLoginLockout(String email) {
        if (email == null || email.isBlank()) return;
        try {
            if (cache.exists(LOCKOUT_UNTIL_PREFIX + email)) {
                throw new TooManyRequestException(
                        "Too many failed login attempts. Please try again later.");
            }
        } catch (TooManyRequestException e) {
            throw e;
        } catch (Exception ex) {
            log.warn("Lockout check unavailable for email hash: {}", ex.getMessage());
        }
    }

    @Override
    public void recordLoginFailure(String email, int threshold, int windowSeconds, int lockoutDurationSeconds) {
        if (email == null || email.isBlank()) return;
        try {
            long failures = cache.incrementWithTtl(LOCKOUT_COUNT_PREFIX + email, windowSeconds);
            if (failures >= threshold) {
                cache.set(LOCKOUT_UNTIL_PREFIX + email, "1", lockoutDurationSeconds);
            }
        } catch (Exception ex) {
            log.warn("Failed to record login failure for lockout tracking: {}", ex.getMessage());
        }
    }

    @Override
    public void clearLoginFailures(String email) {
        if (email == null || email.isBlank()) return;
        try {
            cache.delete(LOCKOUT_COUNT_PREFIX + email);
            cache.delete(LOCKOUT_UNTIL_PREFIX + email);
        } catch (Exception ex) {
            log.warn("Failed to clear login lockout state: {}", ex.getMessage());
        }
    }
}
