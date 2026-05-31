package backend.services.impl;

import backend.services.intf.CacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SingleFlightCacheTest {

    private CacheService cacheService;
    private SingleFlightCache cache;

    @BeforeEach
    void setUp() {
        cacheService = mock(CacheService.class);
        // ThreadPoolTaskExecutor is never reached: checkSampleRate defaults to 0.0
        // when @Value is not injected, so maybeScheduleEarlyRefresh returns immediately.
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
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
}
