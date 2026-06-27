package backend.services.impl.auth;

import backend.exceptions.http.BadRequestException;
import backend.services.intf.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MfaOtpServiceImplTest {

    private CacheService cacheService;
    private MfaOtpServiceImpl service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        cacheService = mock(CacheService.class);
        service = new MfaOtpServiceImpl(cacheService, new ObjectMapper());
    }

    // ─── generateEmailOtp ────────────────────────────────────────────────────

    @Test
    void generateEmailOtp_storesCodeWithCorrectKeyAndTtl() {
        String code = service.generateEmailOtp(USER_ID);

        ArgumentCaptor<String> keyCaptor   = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long>   ttlCaptor   = ArgumentCaptor.forClass(Long.class);
        verify(cacheService).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertEquals(MfaOtpServiceImpl.EMAIL_OTP_PREFIX + USER_ID, keyCaptor.getValue());
        assertEquals(code, valueCaptor.getValue());
        assertEquals(MfaOtpServiceImpl.OTP_TTL_SECONDS, ttlCaptor.getValue());
    }

    @Test
    void generateEmailOtp_returnsSixDigitCode() {
        String code = service.generateEmailOtp(USER_ID);
        assertTrue(code.matches("\\d{6}"), "OTP should be exactly 6 digits, got: " + code);
    }

    // ─── verifyEmailOtp ──────────────────────────────────────────────────────

    @Test
    void verifyEmailOtp_correctCode_doesNotThrow() {
        when(cacheService.getAndDelete(MfaOtpServiceImpl.EMAIL_OTP_PREFIX + USER_ID)).thenReturn("123456");
        assertDoesNotThrow(() -> service.verifyEmailOtp(USER_ID, "123456"));
    }

    @Test
    void verifyEmailOtp_incorrectCode_throwsBadRequest() {
        when(cacheService.getAndDelete(MfaOtpServiceImpl.EMAIL_OTP_PREFIX + USER_ID)).thenReturn("123456");
        assertThrows(BadRequestException.class, () -> service.verifyEmailOtp(USER_ID, "999999"));
    }

    @Test
    void verifyEmailOtp_expiredEntry_throwsBadRequest() {
        when(cacheService.getAndDelete(MfaOtpServiceImpl.EMAIL_OTP_PREFIX + USER_ID)).thenReturn(null);
        assertThrows(BadRequestException.class, () -> service.verifyEmailOtp(USER_ID, "123456"));
    }

    @Test
    void verifyEmailOtp_usesAtomicGetAndDelete() {
        when(cacheService.getAndDelete(anyString())).thenReturn("123456");
        service.verifyEmailOtp(USER_ID, "123456");

        verify(cacheService).getAndDelete(MfaOtpServiceImpl.EMAIL_OTP_PREFIX + USER_ID);
        verify(cacheService, never()).get(anyString());
        verify(cacheService, never()).delete(anyString());
    }

    // ─── generateSmsOtp ──────────────────────────────────────────────────────

    @Test
    void generateSmsOtp_storesJsonPayloadWithCorrectKeyAndTtl() throws Exception {
        String phone = "+15551234567";
        String code  = service.generateSmsOtp(USER_ID, phone);

        ArgumentCaptor<String> keyCaptor   = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long>   ttlCaptor   = ArgumentCaptor.forClass(Long.class);
        verify(cacheService).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertEquals(MfaOtpServiceImpl.SMS_OTP_PREFIX + USER_ID, keyCaptor.getValue());
        assertEquals(MfaOtpServiceImpl.OTP_TTL_SECONDS, ttlCaptor.getValue());

        String json = valueCaptor.getValue();
        assertTrue(json.contains("\"code\":\"" + code + "\""), "JSON should contain the code");
        assertTrue(json.contains("\"phone\":\"" + phone + "\""), "JSON should contain the phone number");
    }

    @Test
    void generateSmsOtp_returnsSixDigitCode() {
        String code = service.generateSmsOtp(USER_ID, "+15551234567");
        assertTrue(code.matches("\\d{6}"), "OTP should be exactly 6 digits, got: " + code);
    }

    // ─── verifySmsOtp ────────────────────────────────────────────────────────

    @Test
    void verifySmsOtp_correctCode_returnsPhoneNumber() {
        String json = "{\"code\":\"123456\",\"phone\":\"+15551234567\"}";
        when(cacheService.getAndDelete(MfaOtpServiceImpl.SMS_OTP_PREFIX + USER_ID)).thenReturn(json);

        String phone = service.verifySmsOtp(USER_ID, "123456");

        assertEquals("+15551234567", phone);
    }

    @Test
    void verifySmsOtp_incorrectCode_throwsBadRequest() {
        String json = "{\"code\":\"123456\",\"phone\":\"+15551234567\"}";
        when(cacheService.getAndDelete(MfaOtpServiceImpl.SMS_OTP_PREFIX + USER_ID)).thenReturn(json);

        assertThrows(BadRequestException.class, () -> service.verifySmsOtp(USER_ID, "999999"));
    }

    @Test
    void verifySmsOtp_expiredEntry_throwsBadRequest() {
        when(cacheService.getAndDelete(MfaOtpServiceImpl.SMS_OTP_PREFIX + USER_ID)).thenReturn(null);
        assertThrows(BadRequestException.class, () -> service.verifySmsOtp(USER_ID, "123456"));
    }

    @Test
    void verifySmsOtp_usesAtomicGetAndDelete() {
        String json = "{\"code\":\"123456\",\"phone\":\"+15551234567\"}";
        when(cacheService.getAndDelete(anyString())).thenReturn(json);
        service.verifySmsOtp(USER_ID, "123456");

        verify(cacheService).getAndDelete(MfaOtpServiceImpl.SMS_OTP_PREFIX + USER_ID);
        verify(cacheService, never()).get(anyString());
        verify(cacheService, never()).delete(anyString());
    }
}
