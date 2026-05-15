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
}
