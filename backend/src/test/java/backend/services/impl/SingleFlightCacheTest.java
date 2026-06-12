package backend.services.impl;

import backend.services.intf.CacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SingleFlightCacheTest {

    private CacheService cacheService;
    private ThreadPoolTaskExecutor executor;
    private SingleFlightCache cache;

    @BeforeEach
    void setUp() {
        cacheService = mock(CacheService.class);
        executor = mock(ThreadPoolTaskExecutor.class);
        cache = new SingleFlightCache(cacheService, new ObjectMapper(), executor);
    }

    // ─── getOrLoad — cache hit ────────────────────────────────────────────────

    @Test
    void getOrLoad_cacheHit_returnsDeserializedValueWithoutCallingLoader() {
        when(cacheService.get("my-key")).thenReturn("\"hello\"");

        String result = cache.getOrLoad("my-key", 300, () -> "from-db", String.class);

        assertEquals("hello", result);
        verify(cacheService, never()).set(any(), any(), anyLong());
    }

    @Test
    void getOrLoad_cacheHit_withTypeReference_returnsDeserializedList() {
        when(cacheService.get("list-key")).thenReturn("[\"a\",\"b\"]");

        List<String> result = cache.getOrLoad("list-key", 300,
                () -> List.of("from-db"),
                new TypeReference<List<String>>() {});

        assertEquals(List.of("a", "b"), result);
    }

    // ─── getOrLoad — cache miss ───────────────────────────────────────────────

    @Test
    void getOrLoad_cacheMiss_invokesLoaderAndCachesResult() {
        when(cacheService.get("miss-key")).thenReturn(null);

        String result = cache.getOrLoad("miss-key", 300, () -> "from-db", String.class);

        assertEquals("from-db", result);
        verify(cacheService).set(eq("miss-key"), eq("\"from-db\""), eq(300L));
    }

    @Test
    void getOrLoad_cacheMiss_loaderReturnsNull_writesJsonNull() {
        // toJson(null) produces the string "null" (valid JSON), which IS written to cache
        when(cacheService.get("null-key")).thenReturn(null);

        String result = cache.getOrLoad("null-key", 300, () -> null, String.class);

        assertNull(result);
        verify(cacheService).set(eq("null-key"), eq("null"), eq(300L));
    }

    // ─── getOrLoad — error resilience ────────────────────────────────────────

    @Test
    void getOrLoad_cacheReadThrows_stillLoadsFromSupplier() {
        when(cacheService.get("err-key")).thenThrow(new RuntimeException("Redis down"));

        String result = cache.getOrLoad("err-key", 300, () -> "fallback", String.class);

        assertEquals("fallback", result);
    }

    @Test
    void getOrLoad_cacheWriteThrows_stillReturnsLoaderValue() {
        when(cacheService.get("write-err")).thenReturn(null);
        doThrow(new RuntimeException("Redis write failed"))
                .when(cacheService).set(any(), any(), anyLong());

        String result = cache.getOrLoad("write-err", 300, () -> "value", String.class);

        assertEquals("value", result);
    }

    @Test
    void getOrLoad_loaderThrows_propagatesException() {
        when(cacheService.get("ex-key")).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> cache.getOrLoad("ex-key", 300,
                        () -> { throw new RuntimeException("DB error"); },
                        String.class));
    }

    @Test
    void getOrLoad_corruptCachedJson_returnsNull() {
        when(cacheService.get("bad-json")).thenReturn("{not valid json}}}");

        // Deserialize error → null; loader is NOT called since raw != null
        String result = cache.getOrLoad("bad-json", 300, () -> "loader", String.class);

        assertNull(result);
        verify(cacheService, never()).set(any(), any(), anyLong());
    }

    // ─── evict ────────────────────────────────────────────────────────────────

    @Test
    void evict_callsCacheServiceDelete() {
        cache.evict("some-key");
        verify(cacheService).delete("some-key");
    }

    @Test
    void evict_deleteThrows_swallowsException() {
        doThrow(new RuntimeException("Redis down")).when(cacheService).delete("k");
        assertDoesNotThrow(() -> cache.evict("k"));
    }

    // ─── evictByPattern ───────────────────────────────────────────────────────

    @Test
    void evictByPattern_callsDeleteByPattern() {
        cache.evictByPattern("prefix:*");
        verify(cacheService).deleteByPattern("prefix:*");
    }

    @Test
    void evictByPattern_deleteThrows_swallowsException() {
        doThrow(new RuntimeException("Redis down"))
                .when(cacheService).deleteByPattern(any());
        assertDoesNotThrow(() -> cache.evictByPattern("prefix:*"));
    }

    // ─── maybeScheduleEarlyRefresh ────────────────────────────────────────────
    // checkSampleRate = 1.0 → nextDouble() < 1.0 is always true → executor is reached

    @Test
    void getOrLoad_cacheHit_withSampleRate1_submitsRefreshTask() {
        ReflectionTestUtils.setField(cache, "checkSampleRate", 1.0);
        when(cacheService.get("warm-key")).thenReturn("\"value\"");

        cache.getOrLoad("warm-key", 300, () -> "loader", String.class);

        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void getOrLoad_cacheHit_secondCall_deduplicatedByRefreshInFlight() {
        ReflectionTestUtils.setField(cache, "checkSampleRate", 1.0);
        // Simulate a key already in flight so the second submission is skipped
        ConcurrentHashMap<String, Boolean> inFlight =
                (ConcurrentHashMap<String, Boolean>) ReflectionTestUtils.getField(cache, "refreshInFlight");
        inFlight.put("warm-key", Boolean.TRUE);

        when(cacheService.get("warm-key")).thenReturn("\"value\"");
        cache.getOrLoad("warm-key", 300, () -> "loader", String.class);

        // Already in flight — executor must NOT receive a second task
        verify(executor, never()).execute(any());
    }

    @Test
    void getOrLoad_cacheHit_executorThrows_removesFromRefreshInFlight() {
        ReflectionTestUtils.setField(cache, "checkSampleRate", 1.0);
        doThrow(new RuntimeException("Executor rejected"))
                .when(executor).execute(any());
        when(cacheService.get("warm-key")).thenReturn("\"value\"");

        // Must not propagate — the early-refresh path is non-critical
        assertDoesNotThrow(() -> cache.getOrLoad("warm-key", 300, () -> "loader", String.class));

        // Key must be cleared so future refreshes can retry
        ConcurrentHashMap<?, ?> inFlight =
                (ConcurrentHashMap<?, ?>) ReflectionTestUtils.getField(cache, "refreshInFlight");
        assertFalse(inFlight.containsKey("warm-key"));
    }

    // ─── double-check path ────────────────────────────────────────────────────

    @Test
    void getOrLoad_doubleCheckHit_returnsSecondGetWithoutCallingLoader() {
        // first call: cold miss; second call (double-check): another node already populated it
        when(cacheService.get("dc-key"))
                .thenReturn(null)
                .thenReturn("\"from-cache\"");

        String result = cache.getOrLoad("dc-key", 300, () -> "from-loader", String.class);

        assertEquals("from-cache", result);
        verify(cacheService, never()).set(anyString(), anyString(), anyLong());
    }

    // ─── checkTtlAndRefresh — background refresh task ─────────────────────────

    @Test
    void checkTtlAndRefresh_remainingLe0_skipsRefresh() {
        ReflectionTestUtils.setField(cache, "checkSampleRate", 1.0);
        ReflectionTestUtils.setField(cache, "earlyRefreshFraction", 0.20);
        when(cacheService.get("ttl-gone-key")).thenReturn("\"value\"");
        when(cacheService.getTtlSeconds("ttl-gone-key")).thenReturn(-2L);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        cache.getOrLoad("ttl-gone-key", 300, () -> "loader", String.class);
        verify(executor).execute(captor.capture());

        captor.getValue().run();

        verify(cacheService, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    void checkTtlAndRefresh_outsideEarlyWindow_skipsRefresh() {
        ReflectionTestUtils.setField(cache, "checkSampleRate", 1.0);
        ReflectionTestUtils.setField(cache, "earlyRefreshFraction", 0.10);
        when(cacheService.get("outside-key")).thenReturn("\"value\"");
        when(cacheService.getTtlSeconds("outside-key")).thenReturn(200L);
        // earlyWindow = 0.10 * 300 = 30; remaining(200) >= earlyWindow(30) → skip

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        cache.getOrLoad("outside-key", 300, () -> "loader", String.class);
        verify(executor).execute(captor.capture());

        captor.getValue().run();

        verify(cacheService, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    void checkTtlAndRefresh_getTtlThrows_swallowsAndReturns() {
        ReflectionTestUtils.setField(cache, "checkSampleRate", 1.0);
        when(cacheService.get("ttl-err-key")).thenReturn("\"value\"");
        when(cacheService.getTtlSeconds("ttl-err-key")).thenThrow(new RuntimeException("Redis timeout"));

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        cache.getOrLoad("ttl-err-key", 300, () -> "loader", String.class);
        verify(executor).execute(captor.capture());

        assertDoesNotThrow(() -> captor.getValue().run());
        verify(cacheService, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    void checkTtlAndRefresh_lockNotAcquired_skipsLoad() {
        ReflectionTestUtils.setField(cache, "checkSampleRate", 1.0);
        ReflectionTestUtils.setField(cache, "earlyRefreshFraction", 1000.0);
        ReflectionTestUtils.setField(cache, "distributedLockEnabled", true);
        when(cacheService.get("lock-miss-key")).thenReturn("\"value\"");
        when(cacheService.getTtlSeconds("lock-miss-key")).thenReturn(1L);
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(false);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        cache.getOrLoad("lock-miss-key", 300, () -> "from-db", String.class);
        verify(executor).execute(captor.capture());

        captor.getValue().run();

        verify(cacheService, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    void checkTtlAndRefresh_lockAcquired_loadsAndCachesAndUnlocks() {
        ReflectionTestUtils.setField(cache, "checkSampleRate", 1.0);
        ReflectionTestUtils.setField(cache, "earlyRefreshFraction", 1000.0);
        ReflectionTestUtils.setField(cache, "distributedLockEnabled", true);
        when(cacheService.get("lock-hit-key")).thenReturn("\"old-value\"");
        when(cacheService.getTtlSeconds("lock-hit-key")).thenReturn(1L);
        when(cacheService.tryLock(startsWith("refresh-lock:"), anyString(), anyLong())).thenReturn(true);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        cache.getOrLoad("lock-hit-key", 300, () -> "new-value", String.class);
        verify(executor).execute(captor.capture());

        captor.getValue().run();

        verify(cacheService).set(eq("lock-hit-key"), eq("\"new-value\""), eq(300L));
        verify(cacheService).unlock(startsWith("refresh-lock:"), anyString());
    }

    @Test
    void checkTtlAndRefresh_noDistributedLock_loadsAndCachesDirectly() {
        ReflectionTestUtils.setField(cache, "checkSampleRate", 1.0);
        ReflectionTestUtils.setField(cache, "earlyRefreshFraction", 1000.0);
        ReflectionTestUtils.setField(cache, "distributedLockEnabled", false);
        when(cacheService.get("no-lock-key")).thenReturn("\"old-value\"");
        when(cacheService.getTtlSeconds("no-lock-key")).thenReturn(1L);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        cache.getOrLoad("no-lock-key", 300, () -> "refreshed", String.class);
        verify(executor).execute(captor.capture());

        captor.getValue().run();

        verify(cacheService).set(eq("no-lock-key"), eq("\"refreshed\""), eq(300L));
        verify(cacheService, never()).tryLock(any(), any(), anyLong());
    }

    @Test
    void checkTtlAndRefresh_cacheWriteThrows_swallowsException() {
        ReflectionTestUtils.setField(cache, "checkSampleRate", 1.0);
        ReflectionTestUtils.setField(cache, "earlyRefreshFraction", 1000.0);
        ReflectionTestUtils.setField(cache, "distributedLockEnabled", false);
        when(cacheService.get("write-fail-key")).thenReturn("\"old\"");
        when(cacheService.getTtlSeconds("write-fail-key")).thenReturn(1L);
        doThrow(new RuntimeException("Redis write failed"))
                .when(cacheService).set(anyString(), anyString(), anyLong());

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        cache.getOrLoad("write-fail-key", 300, () -> "new", String.class);
        verify(executor).execute(captor.capture());

        assertDoesNotThrow(() -> captor.getValue().run());
    }

    @Test
    void checkTtlAndRefresh_unlockThrows_swallowsException() {
        ReflectionTestUtils.setField(cache, "checkSampleRate", 1.0);
        ReflectionTestUtils.setField(cache, "earlyRefreshFraction", 1000.0);
        ReflectionTestUtils.setField(cache, "distributedLockEnabled", true);
        when(cacheService.get("unlock-err-key")).thenReturn("\"old\"");
        when(cacheService.getTtlSeconds("unlock-err-key")).thenReturn(1L);
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        doThrow(new RuntimeException("Redis unlock failed"))
                .when(cacheService).unlock(anyString(), anyString());

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        cache.getOrLoad("unlock-err-key", 300, () -> "new", String.class);
        verify(executor).execute(captor.capture());

        assertDoesNotThrow(() -> captor.getValue().run());
    }

    // ─── fromJson with TypeReference — deserialize error ─────────────────────

    @Test
    void getOrLoad_corruptCachedJson_withTypeReference_returnsNull() {
        when(cacheService.get("bad-ref-key")).thenReturn("{not: valid}}}");

        List<String> result = cache.getOrLoad("bad-ref-key", 300,
                () -> List.of("loader"),
                new TypeReference<List<String>>() {});

        assertNull(result);
        verify(cacheService, never()).set(anyString(), anyString(), anyLong());
    }
}
