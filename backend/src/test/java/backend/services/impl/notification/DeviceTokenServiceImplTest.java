package backend.services.impl.notification;

import backend.models.core.UserDevice;
import backend.repositories.UserDeviceRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceTokenServiceImplTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final String FCM_TOKEN = "fcm-abc-123";
    private static final String APNS_TOKEN = "apns-abc-123";

    private UserDeviceRepository userDeviceRepository;
    private DeviceTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        userDeviceRepository = mock(UserDeviceRepository.class);
        service = new DeviceTokenServiceImpl(userDeviceRepository);
        when(userDeviceRepository.save(any(UserDevice.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── registerToken ────────────────────────────────────────────────────────

    @Test
    void registerToken_android_setsFcmToken() {
        UserDevice device = emptyDevice();
        when(userDeviceRepository.findByUserId(USER_ID)).thenReturn(List.of(device));

        service.registerToken(USER_ID, "ANDROID", FCM_TOKEN);

        ArgumentCaptor<UserDevice> captor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(captor.capture());
        assert FCM_TOKEN.equals(captor.getValue().getFcmToken());
        assertNotNull(captor.getValue().getTokenUpdatedAt());
    }

    @Test
    void registerToken_ios_setsApnsToken() {
        UserDevice device = emptyDevice();
        when(userDeviceRepository.findByUserId(USER_ID)).thenReturn(List.of(device));

        service.registerToken(USER_ID, "IOS", APNS_TOKEN);

        ArgumentCaptor<UserDevice> captor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(captor.capture());
        assert APNS_TOKEN.equals(captor.getValue().getApnsToken());
        assertNotNull(captor.getValue().getTokenUpdatedAt());
    }

    @Test
    void registerToken_tokenAlreadyPresent_skips() {
        UserDevice device = emptyDevice();
        device.setFcmToken(FCM_TOKEN);
        when(userDeviceRepository.findByUserId(USER_ID)).thenReturn(List.of(device));

        service.registerToken(USER_ID, "ANDROID", FCM_TOKEN);

        verify(userDeviceRepository, never()).save(any());
    }

    // ─── revokeToken ─────────────────────────────────────────────────────────

    @Test
    void revokeToken_clearsFcmToken() {
        UserDevice device = emptyDevice();
        device.setFcmToken(FCM_TOKEN);
        when(userDeviceRepository.findByUserId(USER_ID)).thenReturn(List.of(device));

        service.revokeToken(USER_ID, FCM_TOKEN);

        ArgumentCaptor<UserDevice> captor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(captor.capture());
        assertNull(captor.getValue().getFcmToken());
    }

    @Test
    void revokeToken_clearsApnsToken() {
        UserDevice device = emptyDevice();
        device.setApnsToken(APNS_TOKEN);
        when(userDeviceRepository.findByUserId(USER_ID)).thenReturn(List.of(device));

        service.revokeToken(USER_ID, APNS_TOKEN);

        ArgumentCaptor<UserDevice> captor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(captor.capture());
        assertNull(captor.getValue().getApnsToken());
    }

    @Test
    void revokeToken_tokenNotFound_noSave() {
        UserDevice device = emptyDevice();
        device.setFcmToken("other-token");
        when(userDeviceRepository.findByUserId(USER_ID)).thenReturn(List.of(device));

        service.revokeToken(USER_ID, FCM_TOKEN);

        verify(userDeviceRepository, never()).save(any());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private UserDevice emptyDevice() {
        return new UserDevice();
    }
}
