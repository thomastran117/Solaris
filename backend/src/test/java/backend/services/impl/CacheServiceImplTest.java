package backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class CacheServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private SetOperations<String, String> setOps;
    private CacheServiceImpl service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps      = mock(ValueOperations.class);
        setOps        = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        service = new CacheServiceImpl(redisTemplate, "app");
    }

    // ─── namespace prefix ─────────────────────────────────────────────────────

    @Test
    void get_prependsNamespace() {
        when(valueOps.get("app:myKey")).thenReturn("value");
        assertEquals("value", service.get("myKey"));
    }

    @Test
    void blankNamespace_fallsBackToDefault() {
        CacheServiceImpl svc = new CacheServiceImpl(redisTemplate, "  ");
        when(valueOps.get("app:k")).thenReturn("v");
        assertEquals("v", svc.get("k"));
    }

    @Test
    void nullNamespace_fallsBackToDefault() {
        CacheServiceImpl svc = new CacheServiceImpl(redisTemplate, null);
        when(valueOps.get("app:k")).thenReturn("v");
        assertEquals("v", svc.get("k"));
    }

    // ─── set ──────────────────────────────────────────────────────────────────

    @Test
    void set_withPositiveTtl_usesDuration() {
        service.set("k", "v", 60);
        verify(valueOps).set("app:k", "v", Duration.ofSeconds(60));
    }

    @Test
    void set_withZeroTtl_doesNotUseDuration() {
        service.set("k", "v", 0);
        verify(valueOps).set("app:k", "v");
    }

    @Test
    void set_noTtl_callsSimpleSet() {
        service.set("k", "v");
        verify(valueOps).set("app:k", "v");
    }

    // ─── get / delete / exists ────────────────────────────────────────────────

    @Test
    void delete_prependsNamespace() {
        when(redisTemplate.delete("app:k")).thenReturn(true);
        assertTrue(service.delete("k"));
    }

    @Test
    void exists_returnsTrue_whenRedisReturnsTrue() {
        when(redisTemplate.hasKey("app:k")).thenReturn(true);
        assertTrue(service.exists("k"));
    }

    @Test
    void exists_returnsFalse_whenNull() {
        when(redisTemplate.hasKey("app:k")).thenReturn(null);
        assertFalse(service.exists("k"));
    }

    @Test
    void exists_returnsFalse_whenFalse() {
        when(redisTemplate.hasKey("app:k")).thenReturn(false);
        assertFalse(service.exists("k"));
    }

    // ─── getTtlSeconds ────────────────────────────────────────────────────────

    @Test
    void getTtlSeconds_returnsValue() {
        when(redisTemplate.getExpire("app:k", TimeUnit.SECONDS)).thenReturn(120L);
        assertEquals(120L, service.getTtlSeconds("k"));
    }

    @Test
    void getTtlSeconds_returnsNegative2_whenNull() {
        when(redisTemplate.getExpire("app:k", TimeUnit.SECONDS)).thenReturn(null);
        assertEquals(-2L, service.getTtlSeconds("k"));
    }

    // ─── getAndDelete ─────────────────────────────────────────────────────────

    @Test
    void getAndDelete_prependsNamespace() {
        when(valueOps.getAndDelete("app:k")).thenReturn("val");
        assertEquals("val", service.getAndDelete("k"));
    }

    // ─── set operations ───────────────────────────────────────────────────────

    @Test
    void setAdd_withPositiveTtl_setsExpiry() {
        service.setAdd("s", "member", 60);
        verify(setOps).add("app:s", "member");
        verify(redisTemplate).expire(eq("app:s"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void setAdd_withZeroTtl_skipsExpiry() {
        service.setAdd("s", "member", 0);
        verify(setOps).add("app:s", "member");
        verify(redisTemplate, never()).expire(any(), any(Duration.class));
    }

    @Test
    void setMembers_returnsMembers() {
        when(setOps.members("app:s")).thenReturn(Set.of("a", "b"));
        assertEquals(Set.of("a", "b"), service.setMembers("s"));
    }

    @Test
    void setMembers_returnsEmptySet_whenNull() {
        when(setOps.members("app:s")).thenReturn(null);
        assertTrue(service.setMembers("s").isEmpty());
    }

    @Test
    void setRemove_withEmptyMembers_returns0() {
        assertEquals(0L, service.setRemove("s"));
    }

    // ─── tryLock / unlock ─────────────────────────────────────────────────────

    @Test
    void tryLock_returnsTrue_whenAcquired() {
        when(valueOps.setIfAbsent("app:lock", "owner", Duration.ofSeconds(30))).thenReturn(true);
        assertTrue(service.tryLock("lock", "owner", 30));
    }

    @Test
    void tryLock_returnsFalse_whenNotAcquired() {
        when(valueOps.setIfAbsent("app:lock", "owner", Duration.ofSeconds(30))).thenReturn(false);
        assertFalse(service.tryLock("lock", "owner", 30));
    }

    @Test
    void tryLock_returnsFalse_whenNull() {
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(null);
        assertFalse(service.tryLock("lock", "owner", 30));
    }

    @Test
    void unlock_executesLuaScript() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any())).thenReturn(1L);
        service.unlock("lock", "owner");
        verify(redisTemplate).execute(any(DefaultRedisScript.class), eq(List.of("app:lock")), eq("owner"));
    }

    // ─── incrementWithTtl ─────────────────────────────────────────────────────

    @Test
    void incrementWithTtl_returnsCount() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any())).thenReturn(3L);
        assertEquals(3L, service.incrementWithTtl("counter", 60));
    }

    @Test
    void incrementWithTtl_returns0_whenExecuteReturnsNull() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any())).thenReturn(null);
        assertEquals(0L, service.incrementWithTtl("counter", 60));
    }

    // ─── deleteByPattern ──────────────────────────────────────────────────────

    @Test
    void deleteByPattern_deletesMatchedKeys() {
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn("app:prefix:1", "app:prefix:2");
        when(redisTemplate.scan(any())).thenReturn(cursor);
        when(redisTemplate.delete(anyList())).thenReturn(2L);

        long deleted = service.deleteByPattern("prefix:*");

        assertEquals(2L, deleted);
        verify(redisTemplate).delete(anyList());
    }

    @Test
    void deleteByPattern_noMatches_returnsZero() {
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(false);
        when(redisTemplate.scan(any())).thenReturn(cursor);

        assertEquals(0L, service.deleteByPattern("no-match:*"));
        verify(redisTemplate, never()).delete(anyList());
    }
}
