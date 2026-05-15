package backend.services.impl;

import backend.services.intf.CacheService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class CacheServiceImpl implements CacheService {

    private static final String DEFAULT_NAMESPACE = "app";

    private final StringRedisTemplate redisTemplate;
    private final String namespace;

    public CacheServiceImpl(StringRedisTemplate redisTemplate,
                             @Value("${app.cache.namespace:app}") String namespace) {
        this.redisTemplate = redisTemplate;
        this.namespace = namespace != null && !namespace.isBlank() ? namespace : DEFAULT_NAMESPACE;
    }

    private String key(String part) {
        return namespace + ":" + part;
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key(key));
    }

    @Override
    public void set(String key, String value, long ttlSeconds) {
        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set(key(key), value, Duration.ofSeconds(ttlSeconds));
        } else {
            redisTemplate.opsForValue().set(key(key), value);
        }
    }

    @Override
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key(key), value);
    }

    @Override
    public Boolean delete(String key) {
        return redisTemplate.delete(key(key));
    }

    @Override
    public boolean exists(String key) {
        Boolean b = redisTemplate.hasKey(key(key));
        return Boolean.TRUE.equals(b);
    }

    @Override
    public long getTtlSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key(key), TimeUnit.SECONDS);
        if (ttl == null) return -2;
        return ttl;
    }

    @Override
    public void setAdd(String setKey, String member) {
        redisTemplate.opsForSet().add(key(setKey), member);
    }

    @Override
    public void setAdd(String setKey, String member, long ttlSeconds) {
        redisTemplate.opsForSet().add(key(setKey), member);
        if (ttlSeconds > 0) {
            redisTemplate.expire(key(setKey), Duration.ofSeconds(ttlSeconds));
        }
    }

    @Override
    public Set<String> setMembers(String setKey) {
        Set<String> members = redisTemplate.opsForSet().members(key(setKey));
        return members != null ? members : Set.of();
    }

    @Override
    public Long setRemove(String setKey, String... members) {
        if (members == null || members.length == 0) return 0L;
        return redisTemplate.opsForSet().remove(key(setKey), (Object[]) members);
    }

    @Override
    public String getAndDelete(String key) {
        return redisTemplate.opsForValue().getAndDelete(key(key));
    }

    @Override
    public long deleteByPattern(String pattern) {
        // R2-M14: Redis `KEYS` is an O(n) blocking scan over the whole keyspace and
        // freezes the server for the duration. `SCAN` walks in bounded-cost chunks via
        // a cursor, so the cost stays predictable even when the cache has millions of
        // keys. Deletes are batched per chunk to keep round-trips reasonable.
        java.util.List<String> batch = new java.util.ArrayList<>();
        long deleted = 0;
        org.springframework.data.redis.core.ScanOptions opts =
                org.springframework.data.redis.core.ScanOptions.scanOptions().match(key(pattern)).count(500).build();
        try (org.springframework.data.redis.core.Cursor<String> cursor =
                     redisTemplate.scan(opts)) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= 500) {
                    Long n = redisTemplate.delete(batch);
                    if (n != null) deleted += n;
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            Long n = redisTemplate.delete(batch);
            if (n != null) deleted += n;
        }
        return deleted;
    }

    @Override
    public boolean tryLock(String lockKey, String lockValue, long ttlSeconds) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key(lockKey), lockValue, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void unlock(String lockKey, String lockValue) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        redisTemplate.execute(redisScript, Collections.singletonList(key(lockKey)), lockValue);
    }

    /**
     * Atomic INCR + first-hit EXPIRE in a single Redis round-trip. The previous
     * implementation issued INCR and EXPIRE as two separate calls — if the process
     * crashed (or the connection dropped) between them, the counter would lose its
     * TTL and the rate-limit bucket would be permanently broken until manual cleanup.
     */
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL_SCRIPT = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]) "
          + "if n == 1 and tonumber(ARGV[1]) > 0 then "
          + "  redis.call('EXPIRE', KEYS[1], ARGV[1]) "
          + "end "
          + "return n",
            Long.class);

    @Override
    public long incrementWithTtl(String key, long ttlSeconds) {
        String namespacedKey = key(key);
        Long count = redisTemplate.execute(
                INCREMENT_WITH_TTL_SCRIPT,
                Collections.singletonList(namespacedKey),
                Long.toString(ttlSeconds));
        return count == null ? 0L : count;
    }
}
