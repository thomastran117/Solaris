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

    // ─── enforceLoginLockout ──────────────────────────────────────────────────

    @Test
    void enforceLoginLockout_noLockoutKey_doesNotThrow() {
        when(cache.exists("lockout:until:user@example.com")).thenReturn(false);
        assertDoesNotThrow(() -> service.enforceLoginLockout("user@example.com"));
    }

    @Test
    void enforceLoginLockout_lockoutKeyPresent_throwsTooManyRequest() {
        when(cache.exists("lockout:until:user@example.com")).thenReturn(true);
        assertThrows(TooManyRequestException.class,
                () -> service.enforceLoginLockout("user@example.com"));
    }

    @Test
    void enforceLoginLockout_nullEmail_skipsCheck() {
        service.enforceLoginLockout(null);
        verifyNoInteractions(cache);
    }

    @Test
    void enforceLoginLockout_blankEmail_skipsCheck() {
        service.enforceLoginLockout("   ");
        verifyNoInteractions(cache);
    }

    @Test
    void enforceLoginLockout_cacheThrows_failsOpen() {
        when(cache.exists(anyString())).thenThrow(new RuntimeException("Redis down"));
        assertDoesNotThrow(() -> service.enforceLoginLockout("user@example.com"));
    }

    // ─── recordLoginFailure ───────────────────────────────────────────────────

    @Test
    void recordLoginFailure_belowThreshold_doesNotSetLockout() {
        when(cache.incrementWithTtl("lockout:count:user@example.com", 600L)).thenReturn(2L);
        service.recordLoginFailure("user@example.com", 5, 600, 900);
        verify(cache, never()).set(eq("lockout:until:user@example.com"), anyString(), anyLong());
    }

    @Test
    void recordLoginFailure_atThreshold_setsLockoutKey() {
        when(cache.incrementWithTtl("lockout:count:user@example.com", 600L)).thenReturn(5L);
        service.recordLoginFailure("user@example.com", 5, 600, 900);
        verify(cache).set("lockout:until:user@example.com", "1", 900L);
    }

    @Test
    void recordLoginFailure_aboveThreshold_setsLockoutKey() {
        when(cache.incrementWithTtl("lockout:count:user@example.com", 600L)).thenReturn(8L);
        service.recordLoginFailure("user@example.com", 5, 600, 900);
        verify(cache).set("lockout:until:user@example.com", "1", 900L);
    }

    @Test
    void recordLoginFailure_nullEmail_skipsCheck() {
        service.recordLoginFailure(null, 5, 600, 900);
        verifyNoInteractions(cache);
    }

    @Test
    void recordLoginFailure_cacheThrows_failsOpen() {
        when(cache.incrementWithTtl(anyString(), anyLong()))
                .thenThrow(new RuntimeException("Redis down"));
        assertDoesNotThrow(() -> service.recordLoginFailure("user@example.com", 5, 600, 900));
    }

    // ─── clearLoginFailures ───────────────────────────────────────────────────

    @Test
    void clearLoginFailures_deletesBothKeys() {
        service.clearLoginFailures("user@example.com");
        verify(cache).delete("lockout:count:user@example.com");
        verify(cache).delete("lockout:until:user@example.com");
    }

    @Test
    void clearLoginFailures_nullEmail_skipsCheck() {
        service.clearLoginFailures(null);
        verifyNoInteractions(cache);
    }

    @Test
    void clearLoginFailures_blankEmail_skipsCheck() {
        service.clearLoginFailures("  ");
        verifyNoInteractions(cache);
    }

    @Test
    void clearLoginFailures_cacheThrows_failsOpen() {
        doThrow(new RuntimeException("Redis down")).when(cache).delete(anyString());
        assertDoesNotThrow(() -> service.clearLoginFailures("user@example.com"));
    }
}
