package backend.services.impl.auth;

import backend.configurations.environment.EnvironmentSetting;
import backend.exceptions.http.BadRequestException;
import backend.services.intf.CacheService;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailVerificationServiceImplTest {

    private CacheService cache;
    private EmailService emailService;
    private EnvironmentSetting env;
    private EnvironmentSetting.Email emailConf;
    private EmailVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        cache = mock(CacheService.class);
        emailService = mock(EmailService.class);
        env = mock(EnvironmentSetting.class);
        emailConf = mock(EnvironmentSetting.Email.class);
        when(env.getEmail()).thenReturn(emailConf);
        when(emailConf.getVerificationTokenTtlSeconds()).thenReturn(86400L);
        service = new EmailVerificationServiceImpl(cache, emailService, env);
    }

    // ─── initiateVerification ────────────────────────────────────────────────

    @Test
    void initiateVerification_storesUserIdInCacheWithConfiguredTtl() {
        UUID userId = TestIds.uuid(1);

        service.initiateVerification(userId, "user@example.com");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(cache).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertTrue(keyCaptor.getValue().startsWith("email:verify:"));
        assertEquals(userId.toString(), valueCaptor.getValue());
        assertEquals(86400L, ttlCaptor.getValue());
    }

    @Test
    void initiateVerification_sendsVerificationEmailToAddress() {
        UUID userId = TestIds.uuid(1);

        service.initiateVerification(userId, "user@example.com");

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(emailCaptor.capture(), tokenCaptor.capture());

        assertEquals("user@example.com", emailCaptor.getValue());
        assertNotNull(tokenCaptor.getValue());
    }

    // ─── consumeVerificationToken ────────────────────────────────────────────

    @Test
    void consumeVerificationToken_nullToken_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> service.consumeVerificationToken(null));
    }

    @Test
    void consumeVerificationToken_blankToken_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> service.consumeVerificationToken("   "));
    }

    @Test
    void consumeVerificationToken_notInCache_throwsBadRequest() {
        when(cache.getAndDelete(anyString())).thenReturn(null);

        assertThrows(BadRequestException.class,
                () -> service.consumeVerificationToken("nonexistent-token"));
    }

    @Test
    void consumeVerificationToken_malformedUuid_throwsBadRequest() {
        when(cache.getAndDelete(anyString())).thenReturn("not-a-uuid");

        assertThrows(BadRequestException.class,
                () -> service.consumeVerificationToken("some-token"));
    }

    @Test
    void consumeVerificationToken_validToken_returnsUserId() {
        UUID userId = TestIds.uuid(7);
        when(cache.getAndDelete("email:verify:valid-token")).thenReturn(userId.toString());

        UUID result = service.consumeVerificationToken("valid-token");

        assertEquals(userId, result);
    }

    @Test
    void consumeVerificationToken_atomicallyDeletesToken() {
        UUID userId = TestIds.uuid(3);
        when(cache.getAndDelete("email:verify:tok")).thenReturn(userId.toString());

        service.consumeVerificationToken("tok");

        verify(cache).getAndDelete("email:verify:tok");
        verify(cache, never()).get(anyString());
        verify(cache, never()).delete(anyString());
    }
}
