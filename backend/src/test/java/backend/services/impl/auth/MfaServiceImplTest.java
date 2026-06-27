package backend.services.impl.auth;

import backend.dtos.responses.mfa.MfaCredentialResponse;
import backend.dtos.responses.mfa.TotpEnrollmentResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.MfaCredential;
import backend.models.core.User;
import backend.models.enums.MfaType;
import backend.repositories.MfaCredentialRepository;
import backend.services.intf.CacheService;
import backend.services.intf.auth.MfaOtpService;
import backend.services.intf.auth.TotpService;
import backend.services.intf.auth.UserService;
import backend.services.intf.notification.SmsService;
import backend.services.intf.support.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MfaServiceImplTest {

    private MfaCredentialRepository mfaRepo;
    private MfaOtpService mfaOtpService;
    private TotpService totpService;
    private CacheService cacheService;
    private EmailService emailService;
    private SmsService smsService;
    private UserService userService;
    private MfaServiceImpl service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String USER_EMAIL = "user@example.com";
    private static final String PHONE = "+15551234567";

    @BeforeEach
    void setUp() {
        mfaRepo       = mock(MfaCredentialRepository.class);
        mfaOtpService = mock(MfaOtpService.class);
        totpService   = mock(TotpService.class);
        cacheService  = mock(CacheService.class);
        emailService  = mock(EmailService.class);
        smsService    = mock(SmsService.class);
        userService   = mock(UserService.class);
        service = new MfaServiceImpl(mfaRepo, mfaOtpService, totpService,
                cacheService, emailService, smsService, userService);

        User user = new User();
        user.setEmail(USER_EMAIL);
        when(userService.getUserByID(USER_ID)).thenReturn(user);
        when(mfaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── initiateEmailEnrollment ─────────────────────────────────────────────

    @Test
    void initiateEmailEnrollment_generatesOtpAndSendsEmail() {
        when(mfaOtpService.generateEmailOtp(USER_ID)).thenReturn("123456");

        service.initiateEmailEnrollment(USER_ID);

        verify(mfaOtpService).generateEmailOtp(USER_ID);
        verify(emailService).sendMfaOtpEmail(USER_EMAIL, "123456");
    }

    // ─── verifyEmailEnrollment ───────────────────────────────────────────────

    @Test
    void verifyEmailEnrollment_validCode_savesVerifiedCredential() {
        when(mfaRepo.findByUserIdAndType(USER_ID, MfaType.EMAIL)).thenReturn(Optional.empty());

        service.verifyEmailEnrollment(USER_ID, "123456");

        verify(mfaOtpService).verifyEmailOtp(USER_ID, "123456");
        verify(mfaRepo).save(argThat(c ->
                c.getType() == MfaType.EMAIL && c.isVerified() && USER_EMAIL.equals(c.getTarget())));
    }

    @Test
    void verifyEmailEnrollment_existingCredential_updatesInPlace() {
        MfaCredential existing = existingCredential(MfaType.EMAIL);
        when(mfaRepo.findByUserIdAndType(USER_ID, MfaType.EMAIL)).thenReturn(Optional.of(existing));

        service.verifyEmailEnrollment(USER_ID, "123456");

        verify(mfaRepo).save(same(existing));
    }

    @Test
    void verifyEmailEnrollment_invalidCode_propagatesBadRequest() {
        doThrow(new BadRequestException("Invalid")).when(mfaOtpService).verifyEmailOtp(any(), any());
        assertThrows(BadRequestException.class, () -> service.verifyEmailEnrollment(USER_ID, "000000"));
        verify(mfaRepo, never()).save(any());
    }

    // ─── initiateSmsEnrollment ───────────────────────────────────────────────

    @Test
    void initiateSmsEnrollment_validPhone_generatesOtpAndSendsSms() {
        when(mfaOtpService.generateSmsOtp(USER_ID, PHONE)).thenReturn("654321");

        service.initiateSmsEnrollment(USER_ID, PHONE);

        verify(mfaOtpService).generateSmsOtp(USER_ID, PHONE);
        verify(smsService).sendSms(eq(PHONE), contains("654321"));
    }

    // ─── verifySmsEnrollment ─────────────────────────────────────────────────

    @Test
    void verifySmsEnrollment_validCode_savesCredentialWithPhone() {
        when(mfaOtpService.verifySmsOtp(USER_ID, "654321")).thenReturn(PHONE);
        when(mfaRepo.findByUserIdAndType(USER_ID, MfaType.SMS)).thenReturn(Optional.empty());

        service.verifySmsEnrollment(USER_ID, "654321");

        verify(mfaRepo).save(argThat(c ->
                c.getType() == MfaType.SMS && c.isVerified() && PHONE.equals(c.getTarget())));
    }

    @Test
    void verifySmsEnrollment_existingCredential_updatesInPlace() {
        when(mfaOtpService.verifySmsOtp(USER_ID, "654321")).thenReturn(PHONE);
        MfaCredential existing = existingCredential(MfaType.SMS);
        when(mfaRepo.findByUserIdAndType(USER_ID, MfaType.SMS)).thenReturn(Optional.of(existing));

        service.verifySmsEnrollment(USER_ID, "654321");

        verify(mfaRepo).save(same(existing));
    }

    @Test
    void verifySmsEnrollment_invalidCode_propagatesBadRequest() {
        doThrow(new BadRequestException("Invalid")).when(mfaOtpService).verifySmsOtp(any(), any());
        assertThrows(BadRequestException.class, () -> service.verifySmsEnrollment(USER_ID, "000000"));
        verify(mfaRepo, never()).save(any());
    }

    // ─── initiateTotpEnrollment ──────────────────────────────────────────────

    @Test
    void initiateTotpEnrollment_storesPendingSecretInCache() {
        when(totpService.generateSecret()).thenReturn("TESTSECRET");
        when(totpService.getOtpAuthUri(any(), any())).thenReturn("otpauth://...");

        service.initiateTotpEnrollment(USER_ID);

        verify(cacheService).set(
                eq(MfaServiceImpl.TOTP_PENDING_PREFIX + USER_ID),
                eq("TESTSECRET"),
                eq(MfaServiceImpl.TOTP_PENDING_TTL));
    }

    @Test
    void initiateTotpEnrollment_returnsSecretAndUri() {
        when(totpService.generateSecret()).thenReturn("MYSECRET");
        when(totpService.getOtpAuthUri("MYSECRET", USER_EMAIL)).thenReturn("otpauth://totp/ShopWave:user@example.com?secret=MYSECRET");

        TotpEnrollmentResponse response = service.initiateTotpEnrollment(USER_ID);

        assertEquals("MYSECRET", response.secret());
        assertTrue(response.otpAuthUri().contains("MYSECRET"));
    }

    // ─── verifyTotpEnrollment ────────────────────────────────────────────────

    @Test
    void verifyTotpEnrollment_validCode_savesCredentialAndReturnsBackupCodes() {
        when(cacheService.get(MfaServiceImpl.TOTP_PENDING_PREFIX + USER_ID)).thenReturn("MYSECRET");
        when(totpService.verifyCode("MYSECRET", "123456")).thenReturn(true);
        when(totpService.generateBackupCodes(MfaServiceImpl.BACKUP_CODE_COUNT))
                .thenReturn(List.of("CODE1", "CODE2", "CODE3", "CODE4", "CODE5", "CODE6", "CODE7", "CODE8"));
        when(totpService.hashBackupCode(anyString())).thenReturn("$2a$10$hash");
        when(mfaRepo.findByUserIdAndType(USER_ID, MfaType.TOTP)).thenReturn(Optional.empty());

        List<String> result = service.verifyTotpEnrollment(USER_ID, "123456");

        assertEquals(8, result.size());
        verify(mfaRepo).save(argThat(c ->
                c.getType() == MfaType.TOTP && c.isVerified() && "MYSECRET".equals(c.getSecret())));
        // Pending secret is consumed only after a successful enrollment.
        verify(cacheService).delete(MfaServiceImpl.TOTP_PENDING_PREFIX + USER_ID);
    }

    @Test
    void verifyTotpEnrollment_existingCredential_updatesInPlace() {
        when(cacheService.get(anyString())).thenReturn("MYSECRET");
        when(totpService.verifyCode(any(), any())).thenReturn(true);
        when(totpService.generateBackupCodes(anyInt())).thenReturn(List.of("C1","C2","C3","C4","C5","C6","C7","C8"));
        when(totpService.hashBackupCode(anyString())).thenReturn("hash");
        MfaCredential existing = existingCredential(MfaType.TOTP);
        when(mfaRepo.findByUserIdAndType(USER_ID, MfaType.TOTP)).thenReturn(Optional.of(existing));

        service.verifyTotpEnrollment(USER_ID, "123456");

        verify(mfaRepo).save(same(existing));
    }

    @Test
    void verifyTotpEnrollment_noPendingSecret_throwsBadRequest() {
        when(cacheService.get(anyString())).thenReturn(null);
        assertThrows(BadRequestException.class, () -> service.verifyTotpEnrollment(USER_ID, "123456"));
        verify(mfaRepo, never()).save(any());
    }

    @Test
    void verifyTotpEnrollment_invalidCode_throwsBadRequestAndKeepsPendingSecret() {
        when(cacheService.get(anyString())).thenReturn("MYSECRET");
        when(totpService.verifyCode("MYSECRET", "000000")).thenReturn(false);
        assertThrows(BadRequestException.class, () -> service.verifyTotpEnrollment(USER_ID, "000000"));
        verify(mfaRepo, never()).save(any());
        // A wrong code must NOT consume the pending secret — the user can retry without re-scanning.
        verify(cacheService, never()).delete(anyString());
        verify(cacheService, never()).getAndDelete(anyString());
    }

    // ─── listMethods ─────────────────────────────────────────────────────────

    @Test
    void listMethods_returnsResponsesFromRepository() {
        MfaCredential c = existingCredential(MfaType.EMAIL);
        c.setVerified(true);
        c.setEnabled(true);
        c.setTarget(USER_EMAIL);
        when(mfaRepo.findByUserId(USER_ID)).thenReturn(List.of(c));

        List<MfaCredentialResponse> result = service.listMethods(USER_ID);

        assertEquals(1, result.size());
        assertEquals(MfaType.EMAIL, result.get(0).type());
        assertTrue(result.get(0).verified());
        assertTrue(result.get(0).enabled());
    }

    // ─── setMethodEnabled ────────────────────────────────────────────────────

    @Test
    void setMethodEnabled_disablesEnrolledMethod() {
        MfaCredential c = existingCredential(MfaType.TOTP);
        c.setVerified(true);
        c.setEnabled(true);
        when(mfaRepo.findByUserIdAndType(USER_ID, MfaType.TOTP)).thenReturn(Optional.of(c));

        MfaCredentialResponse result = service.setMethodEnabled(USER_ID, MfaType.TOTP, false);

        assertFalse(c.isEnabled());
        assertFalse(result.enabled());
        verify(mfaRepo).save(c);
    }

    @Test
    void setMethodEnabled_reEnablesDisabledMethod() {
        MfaCredential c = existingCredential(MfaType.SMS);
        c.setVerified(true);
        c.setEnabled(false);
        when(mfaRepo.findByUserIdAndType(USER_ID, MfaType.SMS)).thenReturn(Optional.of(c));

        MfaCredentialResponse result = service.setMethodEnabled(USER_ID, MfaType.SMS, true);

        assertTrue(c.isEnabled());
        assertTrue(result.enabled());
        verify(mfaRepo).save(c);
    }

    @Test
    void setMethodEnabled_notEnrolled_throwsResourceNotFound() {
        when(mfaRepo.findByUserIdAndType(USER_ID, MfaType.TOTP)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.setMethodEnabled(USER_ID, MfaType.TOTP, false));
        verify(mfaRepo, never()).save(any());
    }

    // ─── removeMethod ─────────────────────────────────────────────────────────

    @Test
    void removeMethod_existingMethod_deletesCredential() {
        MfaCredential c = existingCredential(MfaType.SMS);
        when(mfaRepo.findByUserIdAndType(USER_ID, MfaType.SMS)).thenReturn(Optional.of(c));

        service.removeMethod(USER_ID, MfaType.SMS);

        verify(mfaRepo).delete(c);
    }

    @Test
    void removeMethod_notEnrolled_throwsResourceNotFound() {
        when(mfaRepo.findByUserIdAndType(USER_ID, MfaType.TOTP)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.removeMethod(USER_ID, MfaType.TOTP));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private MfaCredential existingCredential(MfaType type) {
        MfaCredential c = new MfaCredential();
        c.setType(type);
        return c;
    }
}
