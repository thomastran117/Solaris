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
        String k = key(key);
        String value = redisTemplate.opsForValue().get(k);
        if (value != null) {
            redisTemplate.delete(k);
        }
        return value;
    }

    @Override
    public long deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(key(pattern));
        if (keys == null || keys.isEmpty()) return 0;
        Long deleted = redisTemplate.delete(keys);
        return deleted != null ? deleted : 0;
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
}
