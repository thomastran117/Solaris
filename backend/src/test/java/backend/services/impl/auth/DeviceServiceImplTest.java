package backend.services.impl.auth;

import backend.configurations.environment.EnvironmentSetting;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.http.ClientInfo;
import backend.http.DeviceType;
import backend.models.core.User;
import backend.models.core.UserDevice;
import backend.repositories.UserDeviceRepository;
import backend.repositories.UserRepository;
import backend.services.intf.CacheService;
import backend.services.intf.auth.DeviceService;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceServiceImplTest {

    private UserDeviceRepository userDeviceRepository;
    private UserRepository userRepository;
    private CacheService cache;
    private EmailService emailService;
    private EnvironmentSetting env;
    private EnvironmentSetting.Email emailConf;
    private DeviceServiceImpl service;

    private static final ClientInfo CLIENT_INFO = new ClientInfo(
            "1.2.3.4", DeviceType.DESKTOP, "Chrome", "Windows", "Mozilla/5.0 (Windows NT 10.0)"
    );

    @BeforeEach
    void setUp() {
        userDeviceRepository = mock(UserDeviceRepository.class);
        userRepository = mock(UserRepository.class);
        cache = mock(CacheService.class);
        emailService = mock(EmailService.class);
        env = mock(EnvironmentSetting.class);
        emailConf = mock(EnvironmentSetting.Email.class);
        when(env.getEmail()).thenReturn(emailConf);
        when(emailConf.getDeviceVerificationTokenTtlSeconds()).thenReturn(600L);
        service = new DeviceServiceImpl(userDeviceRepository, userRepository, cache, emailService, env);
    }

    // ─── computeFingerprint ──────────────────────────────────────────────────

    @Test
    void computeFingerprint_nullInput_returnsHashOfEmptyString() {
        String fp1 = service.computeFingerprint(null);
        String fp2 = service.computeFingerprint("");
        assertEquals(fp1, fp2);
        assertNotNull(fp1);
    }

    @Test
    void computeFingerprint_sameInput_isIdempotent() {
        String ua = "Mozilla/5.0";
        assertEquals(service.computeFingerprint(ua), service.computeFingerprint(ua));
    }

    @Test
    void computeFingerprint_returns64HexChars() {
        String fp = service.computeFingerprint("any user agent string");
        assertEquals(64, fp.length());
        assertTrue(fp.matches("[0-9a-f]{64}"));
    }

    // ─── isKnownDevice ───────────────────────────────────────────────────────

    @Test
    void isKnownDevice_deviceFound_returnsTrue() {
        when(userDeviceRepository.findByUserIdAndFingerprint(TestIds.uuid(1), "fp"))
                .thenReturn(Optional.of(new UserDevice()));

        assertTrue(service.isKnownDevice(TestIds.uuid(1), "fp"));
    }

    @Test
    void isKnownDevice_deviceNotFound_returnsFalse() {
        when(userDeviceRepository.findByUserIdAndFingerprint(TestIds.uuid(1), "fp"))
                .thenReturn(Optional.empty());

        assertFalse(service.isKnownDevice(TestIds.uuid(1), "fp"));
    }

    // ─── recordDeviceSeen ────────────────────────────────────────────────────

    @Test
    void recordDeviceSeen_existingDevice_updatesLastSeenAtAndIp() {
        UserDevice existing = new UserDevice();
        existing.setLastIp("old-ip");
        String fp = service.computeFingerprint(CLIENT_INFO.userAgent());
        when(userDeviceRepository.findByUserIdAndFingerprint(TestIds.uuid(1), fp))
                .thenReturn(Optional.of(existing));

        service.recordDeviceSeen(TestIds.uuid(1), CLIENT_INFO);

        ArgumentCaptor<UserDevice> captor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(captor.capture());
        assertEquals("1.2.3.4", captor.getValue().getLastIp());
        assertNotNull(captor.getValue().getLastSeenAt());
    }

    @Test
    void recordDeviceSeen_newDevice_insertsWithAllFields() {
        User userRef = new User();
        userRef.setId(TestIds.uuid(1));
        String fp = service.computeFingerprint(CLIENT_INFO.userAgent());
        when(userDeviceRepository.findByUserIdAndFingerprint(TestIds.uuid(1), fp))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(TestIds.uuid(1))).thenReturn(userRef);

        service.recordDeviceSeen(TestIds.uuid(1), CLIENT_INFO);

        ArgumentCaptor<UserDevice> captor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(captor.capture());
        UserDevice saved = captor.getValue();
        assertEquals(fp, saved.getFingerprint());
        assertEquals(DeviceType.DESKTOP, saved.getDeviceType());
        assertEquals("Chrome", saved.getBrowser());
        assertEquals("Windows", saved.getOs());
        assertEquals("1.2.3.4", saved.getLastIp());
    }

    // ─── initiateDeviceVerification ──────────────────────────────────────────

    @Test
    void initiateDeviceVerification_storesPayloadInCacheWithTtl() {
        service.initiateDeviceVerification(TestIds.uuid(1), "user@example.com", CLIENT_INFO);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(cache).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertTrue(keyCaptor.getValue().startsWith("device:verify:"));
        assertTrue(valueCaptor.getValue().contains(TestIds.uuid(1).toString()));
        assertEquals(600L, ttlCaptor.getValue());
    }

    @Test
    void initiateDeviceVerification_sendsDeviceVerificationEmail() {
        service.initiateDeviceVerification(TestIds.uuid(1), "user@example.com", CLIENT_INFO);

        verify(emailService).sendDeviceVerificationEmail(
                eq("user@example.com"), anyString(),
                eq("Chrome"), eq("Windows"), eq("1.2.3.4"));
    }

    // ─── consumeDeviceVerificationToken ──────────────────────────────────────

    @Test
    void consumeDeviceVerificationToken_nullToken_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> service.consumeDeviceVerificationToken(null));
    }

    @Test
    void consumeDeviceVerificationToken_blankToken_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> service.consumeDeviceVerificationToken("   "));
    }

    @Test
    void consumeDeviceVerificationToken_notInCache_throwsBadRequest() {
        when(cache.getAndDelete(anyString())).thenReturn(null);

        assertThrows(BadRequestException.class,
                () -> service.consumeDeviceVerificationToken("no-such-token"));
    }

    @Test
    void consumeDeviceVerificationToken_malformedPayload_throwsBadRequest() {
        // Only 3 parts instead of 7
        when(cache.getAndDelete(anyString())).thenReturn("a|b|c");

        assertThrows(BadRequestException.class,
                () -> service.consumeDeviceVerificationToken("tok"));
    }

    @Test
    void consumeDeviceVerificationToken_malformedUserId_throwsBadRequest() {
        String payload = "not-a-uuid|fp|Chrome|Windows|DESKTOP|1.2.3.4|Mozilla/5.0";
        when(cache.getAndDelete(anyString())).thenReturn(payload);

        assertThrows(BadRequestException.class,
                () -> service.consumeDeviceVerificationToken("tok"));
    }

    @Test
    void consumeDeviceVerificationToken_validToken_returnsPayload() {
        UUID userId = TestIds.uuid(5);
        String fp = service.computeFingerprint(CLIENT_INFO.userAgent());
        String payload = userId + "|" + fp + "|Chrome|Windows|DESKTOP|1.2.3.4|Mozilla/5.0";
        when(cache.getAndDelete("device:verify:tok")).thenReturn(payload);

        DeviceService.DeviceVerificationPayload result =
                service.consumeDeviceVerificationToken("tok");

        assertEquals(userId, result.userId());
        assertEquals(fp, result.fingerprint());
        assertEquals("Chrome", result.browser());
        assertEquals("Windows", result.os());
        assertEquals(DeviceType.DESKTOP, result.deviceType());
        assertEquals("1.2.3.4", result.ip());
        assertEquals("Mozilla/5.0", result.userAgent());
    }

    @Test
    void consumeDeviceVerificationToken_atomicallyDeletesToken() {
        UUID userId = TestIds.uuid(5);
        String payload = userId + "|fp|Chrome|Windows|DESKTOP|1.2.3.4|Mozilla/5.0";
        when(cache.getAndDelete("device:verify:tok")).thenReturn(payload);

        service.consumeDeviceVerificationToken("tok");

        verify(cache).getAndDelete("device:verify:tok");
        verify(cache, never()).get(anyString());
        verify(cache, never()).delete(anyString());
    }

    // ─── getDevicesForUser ───────────────────────────────────────────────────

    @Test
    void getDevicesForUser_delegatesToRepository() {
        UserDevice d1 = new UserDevice();
        when(userDeviceRepository.findByUserId(TestIds.uuid(1))).thenReturn(List.of(d1));

        List<UserDevice> result = service.getDevicesForUser(TestIds.uuid(1));

        assertEquals(1, result.size());
        assertSame(d1, result.get(0));
    }

    // ─── removeDevice ────────────────────────────────────────────────────────

    @Test
    void removeDevice_ownerCanRemoveDevice() {
        User owner = new User();
        owner.setId(TestIds.uuid(1));
        UserDevice device = new UserDevice();
        device.setUser(owner);
        when(userDeviceRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(device));

        service.removeDevice(TestIds.uuid(1), TestIds.uuid(10));

        verify(userDeviceRepository).deleteById(TestIds.uuid(10));
    }

    @Test
    void removeDevice_nonOwner_throwsForbidden() {
        User owner = new User();
        owner.setId(TestIds.uuid(1));
        UserDevice device = new UserDevice();
        device.setUser(owner);
        when(userDeviceRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(device));

        assertThrows(ForbiddenException.class,
                () -> service.removeDevice(TestIds.uuid(2), TestIds.uuid(10)));
        verify(userDeviceRepository, never()).deleteById(any());
    }

    @Test
    void removeDevice_deviceNotFound_throwsResourceNotFound() {
        when(userDeviceRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.removeDevice(TestIds.uuid(1), TestIds.uuid(99)));
        verify(userDeviceRepository, never()).deleteById(any());
    }
}
