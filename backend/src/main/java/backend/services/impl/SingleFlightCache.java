package backend.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import backend.services.intf.CacheService;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Cache-aside layer with two stampede-prevention mechanisms:
 *
 * 1. Single-flight deduplication — on a cold miss, only one thread loads from DB;
 *    concurrent waiters coalesce on its CompletableFuture.
 *
 * 2. Probabilistic early refresh (XFetch-style) — on a cache hit whose remaining TTL
 *    is within the configured early window, a background thread proactively reloads
 *    the value before it expires, keeping keys perpetually warm under traffic.
 *    The calling thread always returns the cached value immediately with zero added latency.
 */
@Component
public class SingleFlightCache {

    private static final Logger log = LoggerFactory.getLogger(SingleFlightCache.class);

    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor cacheRefreshExecutor;

    // Single-flight: tracks in-progress DB loads for cold misses
    private final ConcurrentHashMap<String, CompletableFuture<Optional<String>>> inFlight = new ConcurrentHashMap<>();

    // Early refresh: prevents submitting duplicate background refresh tasks per JVM
    private final ConcurrentHashMap<String, Boolean> refreshInFlight = new ConcurrentHashMap<>();

    // Stable per-instance identity used as distributed lock owner token
    private final String instanceId = UUID.randomUUID().toString();

    @Value("${app.cache.early-refresh.check-sample-rate:0.1}")
    private double checkSampleRate;

    @Value("${app.cache.early-refresh.fraction:0.20}")
    private double earlyRefreshFraction;

    @Value("${app.cache.early-refresh.distributed-lock-enabled:true}")
    private boolean distributedLockEnabled;

    @Value("${app.cache.early-refresh.lock-ttl-seconds:30}")
    private long lockTtlSeconds;

    public SingleFlightCache(CacheService cacheService,
                             ObjectMapper objectMapper,
                             @Qualifier("cacheRefreshExecutor") ThreadPoolTaskExecutor cacheRefreshExecutor) {
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.cacheRefreshExecutor = cacheRefreshExecutor;
    }

    public <T> T getOrLoad(String key, long ttlSeconds, Supplier<T> loader, Class<T> type) {
        return getOrLoad(key, ttlSeconds, loader, raw -> fromJson(key, raw, type));
    }

    public <T> T getOrLoad(String key, long ttlSeconds, Supplier<T> loader, TypeReference<T> typeRef) {
        return getOrLoad(key, ttlSeconds, loader, raw -> fromJson(key, raw, typeRef));
    }

    public void evict(String key) {
        try {
            cacheService.delete(key);
        } catch (Exception e) {
            log.warn("[CACHE] Evict error for key {}: {}", key, e.getMessage());
        }
    }

    public void evictByPattern(String pattern) {
        try {
            cacheService.deleteByPattern(pattern);
        } catch (Exception e) {
            log.warn("[CACHE] Evict-by-pattern error for pattern {}: {}", pattern, e.getMessage());
        }
    }

    private <T> T getOrLoad(String key, long ttlSeconds, Supplier<T> loader, Function<String, T> deserializer) {
        // Fast path: Redis hit
        String raw = safeGet(key);
        if (raw != null) {
            maybeScheduleEarlyRefresh(key, ttlSeconds, loader);
            return deserializer.apply(raw);
        }

        // Single-flight: register or coalesce
        CompletableFuture<Optional<String>> mine = new CompletableFuture<>();
        CompletableFuture<Optional<String>> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            // Another thread is already loading — wait for its result
            Optional<String> result = existing.join();
            return result.map(deserializer).orElse(null);
        }

        try {
            // Double-check after acquiring the slot (another instance may have populated cache)
            raw = safeGet(key);
            if (raw != null) {
                mine.complete(Optional.of(raw));
                maybeScheduleEarlyRefresh(key, ttlSeconds, loader);
                return deserializer.apply(raw);
            }

            T value = loader.get();
            String json = toJson(value);
            if (json != null) {
                try {
                    cacheService.set(key, json, ttlSeconds);
                } catch (Exception e) {
                    log.warn("[CACHE] Write error for key {}: {}", key, e.getMessage());
                }
            }
            mine.complete(Optional.ofNullable(json));
            return value;
        } catch (Exception e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    /**
     * Hot-path entry point for early refresh. Cost to the calling thread:
     * one ThreadLocalRandom call + one ConcurrentHashMap lookup + one executor submit (10% of hits).
     * All I/O (TTL check, DB load, cache write) happens in a background thread.
     */
    private <T> void maybeScheduleEarlyRefresh(String key, long ttlSeconds, Supplier<T> loader) {
        if (ThreadLocalRandom.current().nextDouble() >= checkSampleRate) return;

        // Per-JVM dedup: skip if a refresh task is already queued or running for this key
        if (refreshInFlight.putIfAbsent(key, Boolean.TRUE) != null) return;

        try {
            cacheRefreshExecutor.execute(() -> checkTtlAndRefresh(key, ttlSeconds, loader));
        } catch (Exception e) {
            // DiscardPolicy shouldn't throw, but guard the cleanup regardless
            refreshInFlight.remove(key);
        }
    }

    /**
     * Background task: checks remaining TTL, applies XFetch-style probability, and if it fires,
     * reloads from DB and resets the cache TTL to the full configured value.
     */
    private <T> void checkTtlAndRefresh(String key, long ttlSeconds, Supplier<T> loader) {
        boolean lockAcquired = false;
        try {
            long remaining;
            try {
                remaining = cacheService.getTtlSeconds(key);
            } catch (Exception e) {
                log.warn("[CACHE] getTtl error for key {}: {}", key, e.getMessage());
                return;
            }

            // Key gone (-2) or has no expiry (-1) — nothing to do
            if (remaining <= 0) return;

            double earlyWindow = earlyRefreshFraction * ttlSeconds;
            if (earlyWindow <= 0 || remaining >= earlyWindow) return;

            // Linear ramp: p = 0 at window boundary, p = 1 at remaining = 0
            double p = 1.0 - (remaining / earlyWindow);
            if (ThreadLocalRandom.current().nextDouble() >= p) return;

            if (distributedLockEnabled) {
                String lockKey = "refresh-lock:" + key;
                try {
                    lockAcquired = cacheService.tryLock(lockKey, instanceId, lockTtlSeconds);
                } catch (Exception e) {
                    log.warn("[CACHE] tryLock error for key {}: {}", key, e.getMessage());
                    return;
                }
                if (!lockAcquired) return;
            }

            log.debug("[CACHE] Early refresh for key {} (remaining={}s)", key, remaining);
            T value = loader.get();
            String json = toJson(value);
            if (json != null) {
                try {
                    cacheService.set(key, json, ttlSeconds);
                } catch (Exception e) {
                    log.warn("[CACHE] Early refresh write error for key {}: {}", key, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.warn("[CACHE] Early refresh error for key {}: {}", key, e.getMessage());
        } finally {
            refreshInFlight.remove(key);
            if (lockAcquired) {
                try {
                    cacheService.unlock("refresh-lock:" + key, instanceId);
                } catch (Exception e) {
                    log.warn("[CACHE] Unlock error for key {}: {}", key, e.getMessage());
                }
            }
        }
    }

    private String safeGet(String key) {
        try {
            return cacheService.get(key);
        } catch (Exception e) {
            log.warn("[CACHE] Read error for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    private <T> T fromJson(String key, String raw, Class<T> type) {
        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            log.warn("[CACHE] Deserialize error for key {}, evicting: {}", key, e.getMessage());
            evict(key);
            return null;
        }
    }

    private <T> T fromJson(String key, String raw, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(raw, typeRef);
        } catch (Exception e) {
            log.warn("[CACHE] Deserialize error for key {}, evicting: {}", key, e.getMessage());
            evict(key);
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[CACHE] Serialize error: {}", e.getMessage());
            return null;
        }
    }
}
