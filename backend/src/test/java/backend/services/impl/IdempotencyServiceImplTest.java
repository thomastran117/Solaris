package backend.services.impl;

import backend.services.intf.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdempotencyServiceImplTest {

    private static final UUID USER_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESULT_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    private CacheService cache;
    private IdempotencyServiceImpl service;

    @BeforeEach
    void setUp() {
        cache   = mock(CacheService.class);
        service = new IdempotencyServiceImpl(cache);
    }

    // ─── lookup ───────────────────────────────────────────────────────────────

    @Test
    void lookup_nullKey_returnsEmpty() {
        Optional<UUID> result = service.lookup("order", USER_ID, null);
        assertTrue(result.isEmpty());
        verifyNoInteractions(cache);
    }

    @Test
    void lookup_blankKey_returnsEmpty() {
        Optional<UUID> result = service.lookup("order", USER_ID, "  ");
        assertTrue(result.isEmpty());
        verifyNoInteractions(cache);
    }

    @Test
    void lookup_cacheMiss_returnsEmpty() {
        when(cache.get(anyString())).thenReturn(null);
        assertTrue(service.lookup("order", USER_ID, "key-1").isEmpty());
    }

    @Test
    void lookup_inFlightMarker_returnsEmpty() {
        when(cache.get(anyString())).thenReturn("__inflight__");
        assertTrue(service.lookup("order", USER_ID, "key-1").isEmpty());
    }

    @Test
    void lookup_validUUID_returnsUUID() {
        when(cache.get(anyString())).thenReturn(RESULT_ID.toString());
        Optional<UUID> result = service.lookup("order", USER_ID, "key-1");
        assertTrue(result.isPresent());
        assertEquals(RESULT_ID, result.get());
    }

    @Test
    void lookup_invalidUUID_throwsIllegalArgument() {
        // The service catches NumberFormatException but UUID.fromString throws
        // IllegalArgumentException — which is not a subtype of NumberFormatException,
        // so the exception propagates. Document the actual behaviour here.
        when(cache.get(anyString())).thenReturn("not-a-uuid");
        assertThrows(IllegalArgumentException.class,
                () -> service.lookup("order", USER_ID, "key-1"));
    }

    @Test
    void lookup_blankCachedValue_returnsEmpty() {
        when(cache.get(anyString())).thenReturn("   ");
        assertTrue(service.lookup("order", USER_ID, "key-1").isEmpty());
    }

    @Test
    void lookup_keyFormatIncludesScopeUserAndKey() {
        when(cache.get("idempotency:order:" + USER_ID + ":idem-key")).thenReturn(RESULT_ID.toString());
        Optional<UUID> result = service.lookup("order", USER_ID, "idem-key");
        assertTrue(result.isPresent());
    }

    // ─── store ────────────────────────────────────────────────────────────────

    @Test
    void store_nullKey_doesNotCache() {
        service.store("order", USER_ID, null, RESULT_ID);
        verifyNoInteractions(cache);
    }

    @Test
    void store_blankKey_doesNotCache() {
        service.store("order", USER_ID, "", RESULT_ID);
        verifyNoInteractions(cache);
    }

    @Test
    void store_validKey_cachesResultIdWith24hTtl() {
        service.store("order", USER_ID, "idem-key", RESULT_ID);
        verify(cache).set(
                eq("idempotency:order:" + USER_ID + ":idem-key"),
                eq(RESULT_ID.toString()),
                eq(24L * 60 * 60));
    }

    // ─── claim ────────────────────────────────────────────────────────────────

    @Test
    void claim_nullKey_returnsTrueWithoutLocking() {
        assertTrue(service.claim("order", USER_ID, null, 30));
        verifyNoInteractions(cache);
    }

    @Test
    void claim_blankKey_returnsTrueWithoutLocking() {
        assertTrue(service.claim("order", USER_ID, "  ", 30));
        verifyNoInteractions(cache);
    }

    @Test
    void claim_lockAcquired_returnsTrue() {
        when(cache.tryLock(anyString(), eq("__inflight__"), eq(30L))).thenReturn(true);
        assertTrue(service.claim("order", USER_ID, "idem-key", 30));
    }

    @Test
    void claim_lockNotAcquired_returnsFalse() {
        when(cache.tryLock(anyString(), eq("__inflight__"), eq(30L))).thenReturn(false);
        assertFalse(service.claim("order", USER_ID, "idem-key", 30));
    }

    @Test
    void claim_keyFormatMatchesLookupKey() {
        when(cache.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        service.claim("order", USER_ID, "idem-key", 30);
        verify(cache).tryLock(
                eq("idempotency:order:" + USER_ID + ":idem-key"),
                anyString(),
                anyLong());
    }
}
