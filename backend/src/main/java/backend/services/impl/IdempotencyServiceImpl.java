package backend.services.impl;

import backend.services.intf.CacheService;
import backend.services.intf.IdempotencyService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    /** Reserved cache value used while the operation is in flight — replaced with the id on success. */
    private static final String IN_FLIGHT_MARKER = "__inflight__";
    /** 24h retention is the conventional default and matches Stripe's idempotency window. */
    private static final long RESULT_TTL_SECONDS = 24L * 60 * 60;

    private final CacheService cache;

    public IdempotencyServiceImpl(CacheService cache) {
        this.cache = cache;
    }

    private static String redisKey(String scope, long userId, String key) {
        return "idempotency:" + scope + ":" + userId + ":" + key;
    }

    @Override
    public Optional<Long> lookup(String scope, long userId, String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        String raw = cache.get(redisKey(scope, userId, key));
        if (raw == null || raw.isBlank() || IN_FLIGHT_MARKER.equals(raw)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(raw));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void store(String scope, long userId, String key, long resultId) {
        if (key == null || key.isBlank()) return;
        cache.set(redisKey(scope, userId, key), Long.toString(resultId), RESULT_TTL_SECONDS);
    }

    @Override
    public boolean claim(String scope, long userId, String key, long claimTtlSeconds) {
        if (key == null || key.isBlank()) return true;
        return cache.tryLock(redisKey(scope, userId, key), IN_FLIGHT_MARKER, claimTtlSeconds);
    }
}
