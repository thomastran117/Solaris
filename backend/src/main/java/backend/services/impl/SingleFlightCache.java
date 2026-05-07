package backend.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import backend.services.intf.CacheService;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Cache-aside layer with single-flight deduplication: when multiple threads concurrently miss the
 * same Redis key, only one proceeds to the DB loader; the rest coalesce on its CompletableFuture.
 * Redis failures on the write path are swallowed so they never break a read.
 */
@Component
public class SingleFlightCache {

    private static final Logger log = LoggerFactory.getLogger(SingleFlightCache.class);

    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CompletableFuture<Optional<String>>> inFlight = new ConcurrentHashMap<>();

    public SingleFlightCache(CacheService cacheService, ObjectMapper objectMapper) {
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    public <T> T getOrLoad(String key, long ttlSeconds, Supplier<T> loader, Class<T> type) {
        return getOrLoad(key, ttlSeconds, loader, raw -> fromJson(raw, type));
    }

    public <T> T getOrLoad(String key, long ttlSeconds, Supplier<T> loader, TypeReference<T> typeRef) {
        return getOrLoad(key, ttlSeconds, loader, raw -> fromJson(raw, typeRef));
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
        if (raw != null) return deserializer.apply(raw);

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

    private String safeGet(String key) {
        try {
            return cacheService.get(key);
        } catch (Exception e) {
            log.warn("[CACHE] Read error for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    private <T> T fromJson(String raw, Class<T> type) {
        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            log.warn("[CACHE] Deserialize error: {}", e.getMessage());
            return null;
        }
    }

    private <T> T fromJson(String raw, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(raw, typeRef);
        } catch (Exception e) {
            log.warn("[CACHE] Deserialize error: {}", e.getMessage());
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
