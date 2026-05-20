package backend.services.impl;

import backend.exceptions.http.TooManyRequestException;
import backend.services.intf.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitServiceImplTest {

    private CacheService cache;
    private RateLimitServiceImpl service;

    @BeforeEach
    void setUp() {
        cache = mock(CacheService.class);
        service = new RateLimitServiceImpl(cache);
    }

    // ─── enforce — pass-through cases ────────────────────────────────────────

    @Test
    void enforce_underLimit_doesNotThrow() {
        when(cache.incrementWithTtl(anyString(), anyLong())).thenReturn(1L);
        assertDoesNotThrow(() -> service.enforce("login", "user1", 5, 60));
    }

    @Test
    void enforce_exactlyAtLimit_doesNotThrow() {
        when(cache.incrementWithTtl(anyString(), anyLong())).thenReturn(5L);
        assertDoesNotThrow(() -> service.enforce("login", "user1", 5, 60));
    }

    // ─── enforce — throws when exceeded ──────────────────────────────────────

    @Test
    void enforce_oneOverLimit_throwsTooManyRequest() {
        when(cache.incrementWithTtl(anyString(), anyLong())).thenReturn(6L);
        assertThrows(TooManyRequestException.class,
                () -> service.enforce("login", "user1", 5, 60));
    }

    @Test
    void enforce_wellOverLimit_throwsTooManyRequest() {
        when(cache.incrementWithTtl(anyString(), anyLong())).thenReturn(100L);
        assertThrows(TooManyRequestException.class,
                () -> service.enforce("api", "ip:1.2.3.4", 10, 3600));
    }

    // ─── enforce — degenerate input skips check ───────────────────────────────

    @Test
    void enforce_nullSubject_skipsCheck() {
        service.enforce("scope", null, 10, 60);
        verifyNoInteractions(cache);
    }

    @Test
    void enforce_blankSubject_skipsCheck() {
        service.enforce("scope", "   ", 10, 60);
        verifyNoInteractions(cache);
    }

    @Test
    void enforce_emptySubject_skipsCheck() {
        service.enforce("scope", "", 10, 60);
        verifyNoInteractions(cache);
    }

    // ─── enforce — cache failure fails open ───────────────────────────────────

    @Test
    void enforce_cacheThrows_failsOpen() {
        when(cache.incrementWithTtl(anyString(), anyLong()))
                .thenThrow(new RuntimeException("Redis down"));
        // Must not throw — fail open per CLAUDE.md
        assertDoesNotThrow(() -> service.enforce("login", "user1", 5, 60));
    }

    // ─── enforce — cache key format ───────────────────────────────────────────

    @Test
    void enforce_keyIncludesScopeAndSubject() {
        when(cache.incrementWithTtl(anyString(), anyLong())).thenReturn(1L);
        service.enforce("checkout", "user-abc", 10, 3600);
        verify(cache).incrementWithTtl(
                eq("ratelimit:checkout:user-abc"),
                eq(3600L));
    }

    @Test
    void enforce_windowSecondsPassedAsTtl() {
        when(cache.incrementWithTtl(anyString(), anyLong())).thenReturn(1L);
        service.enforce("scope", "sub", 5, 120);
        verify(cache).incrementWithTtl(anyString(), eq(120L));
    }
}
