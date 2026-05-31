package backend.kafka.workers;

import backend.events.notification.NotificationEvent;
import backend.models.core.NotificationLog;
import backend.models.core.UserDevice;
import backend.models.core.UserPreference;
import backend.models.enums.NotificationDeliveryStatus;
import backend.repositories.NotificationLogRepository;
import backend.repositories.UserDeviceRepository;
import backend.repositories.UserPreferenceRepository;
import backend.services.intf.notification.PushNotificationService;
import backend.services.intf.notification.SmsService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDispatchWorkerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID ORDER_ID = TestIds.uuid(2);
    private static final String PHONE = "+15550001234";
    private static final String FCM_TOKEN_1 = "fcm-token-1";
    private static final String FCM_TOKEN_2 = "fcm-token-2";

    private UserPreferenceRepository userPreferenceRepository;
    private UserDeviceRepository userDeviceRepository;
    private PushNotificationService pushNotificationService;
    private SmsService smsService;
    private NotificationLogRepository notificationLogRepository;
    private NotificationDispatchWorker worker;

    @BeforeEach
    void setUp() {
        userPreferenceRepository = mock(UserPreferenceRepository.class);
        userDeviceRepository = mock(UserDeviceRepository.class);
        pushNotificationService = mock(PushNotificationService.class);
        smsService = mock(SmsService.class);
        notificationLogRepository = mock(NotificationLogRepository.class);
        worker = new NotificationDispatchWorker(
                userPreferenceRepository, userDeviceRepository,
                pushNotificationService, smsService, notificationLogRepository);
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── dispatch — push ──────────────────────────────────────────────────────

    @Test
    void dispatch_pushEnabled_callsPushForEachToken() {
        UserPreference prefs = prefsWithPush(true, false, null);
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.of(prefs));
        when(userDeviceRepository.findByUserId(USER_ID)).thenReturn(
                List.of(deviceWithFcm(FCM_TOKEN_1), deviceWithFcm(FCM_TOKEN_2)));

        worker.dispatch(shippedEvent());

        verify(pushNotificationService, times(2)).sendPush(anyString(), anyString(), anyString(), any());
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(logCaptor.capture());
        logCaptor.getAllValues().forEach(l -> assertEquals(NotificationDeliveryStatus.SENT, l.getStatus()));
    }

    @Test
    void dispatch_pushEnabled_noTokens_skips() {
        UserPreference prefs = prefsWithPush(true, false, null);
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.of(prefs));
        when(userDeviceRepository.findByUserId(USER_ID)).thenReturn(List.of(deviceWithFcm(null)));

        worker.dispatch(shippedEvent());

        verify(pushNotificationService, never()).sendPush(anyString(), anyString(), anyString(), any());
    }

    @Test
    void dispatch_pushFails_logsFailedAndDoesNotThrow() {
        UserPreference prefs = prefsWithPush(true, false, null);
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.of(prefs));
        when(userDeviceRepository.findByUserId(USER_ID)).thenReturn(List.of(deviceWithFcm(FCM_TOKEN_1)));
        doThrow(new RuntimeException("FCM error")).when(pushNotificationService)
                .sendPush(anyString(), anyString(), anyString(), any());

        assertDoesNotThrow(() -> worker.dispatch(shippedEvent()));

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        assertEquals(NotificationDeliveryStatus.FAILED, logCaptor.getValue().getStatus());
    }

    // ─── dispatch — SMS ───────────────────────────────────────────────────────

    @Test
    void dispatch_smsEnabled_callsSms() {
        UserPreference prefs = prefsWithPush(false, true, PHONE);
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.of(prefs));
        when(userDeviceRepository.findByUserId(USER_ID)).thenReturn(List.of());

        worker.dispatch(shippedEvent());

        verify(smsService).sendSms(eq(PHONE), anyString());
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        assertEquals(NotificationDeliveryStatus.SENT, logCaptor.getValue().getStatus());
    }

    @Test
    void dispatch_smsFails_logsFailedAndDoesNotThrow() {
        UserPreference prefs = prefsWithPush(false, true, PHONE);
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.of(prefs));
        when(userDeviceRepository.findByUserId(USER_ID)).thenReturn(List.of());
        doThrow(new RuntimeException("Twilio error")).when(smsService).sendSms(anyString(), anyString());

        assertDoesNotThrow(() -> worker.dispatch(shippedEvent()));

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        assertEquals(NotificationDeliveryStatus.FAILED, logCaptor.getValue().getStatus());
    }

    @Test
    void dispatch_allChannelsDisabled_nothingSent() {
        UserPreference prefs = prefsWithPush(false, false, null);
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.of(prefs));
        when(userDeviceRepository.findByUserId(USER_ID)).thenReturn(List.of());

        worker.dispatch(shippedEvent());

        verify(pushNotificationService, never()).sendPush(anyString(), anyString(), anyString(), any());
        verify(smsService, never()).sendSms(anyString(), anyString());
        verify(notificationLogRepository, never()).save(any());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private NotificationEvent shippedEvent() {
        return new NotificationEvent.OrderShipped(USER_ID, ORDER_ID, "Alice", "TRK123", "UPS");
    }

    private UserPreference prefsWithPush(boolean push, boolean sms, String phone) {
        UserPreference p = new UserPreference(USER_ID);
        p.setPushEnabled(push);
        p.setSmsEnabled(sms);
        p.setSmsPhoneNumber(phone);
        return p;
    }

    private UserDevice deviceWithFcm(String token) {
        UserDevice d = new UserDevice();
        d.setFcmToken(token);
        return d;
    }
}
