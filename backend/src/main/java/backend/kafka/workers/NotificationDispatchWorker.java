package backend.kafka.workers;

import backend.events.notification.NotificationEvent;
import backend.models.core.NotificationLog;
import backend.models.core.UserDevice;
import backend.models.core.UserPreference;
import backend.models.enums.NotificationChannel;
import backend.models.enums.NotificationDeliveryStatus;
import backend.repositories.NotificationLogRepository;
import backend.repositories.UserDeviceRepository;
import backend.repositories.UserPreferenceRepository;
import backend.services.intf.notification.PushNotificationService;
import backend.services.intf.notification.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class NotificationDispatchWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchWorker.class);

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final PushNotificationService pushNotificationService;
    private final SmsService smsService;
    private final NotificationLogRepository notificationLogRepository;

    public NotificationDispatchWorker(
            UserPreferenceRepository userPreferenceRepository,
            UserDeviceRepository userDeviceRepository,
            PushNotificationService pushNotificationService,
            SmsService smsService,
            NotificationLogRepository notificationLogRepository) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.pushNotificationService = pushNotificationService;
        this.smsService = smsService;
        this.notificationLogRepository = notificationLogRepository;
    }

    public void dispatch(NotificationEvent event) {
        UUID userId = userId(event);
        if (userId == null) return;

        UserPreference prefs = userPreferenceRepository.findById(userId).orElse(null);
        if (prefs == null) return;

        String eventType = event.getClass().getSimpleName();
        String title = buildTitle(event);
        String body = buildBody(event);

        if (prefs.isPushEnabled()) {
            dispatchPush(userId, event, eventType, title, body);
        }

        if (prefs.isSmsEnabled() && prefs.getSmsPhoneNumber() != null && !prefs.getSmsPhoneNumber().isBlank()) {
            dispatchSms(userId, prefs.getSmsPhoneNumber(), eventType, body);
        }
    }

    private void dispatchPush(UUID userId, NotificationEvent event, String eventType,
                               String title, String body) {
        List<UserDevice> devices = userDeviceRepository.findByUserId(userId);
        Map<String, String> data = Map.of("userId", userId.toString(), "eventType", eventType);

        for (UserDevice device : devices) {
            String token = device.getFcmToken() != null ? device.getFcmToken() : device.getApnsToken();
            if (token == null) continue;
            try {
                pushNotificationService.sendPush(token, title, body, data);
                saveLog(userId, NotificationChannel.PUSH, eventType, NotificationDeliveryStatus.SENT, null, null);
            } catch (Exception e) {
                log.warn("[PUSH] Dispatch failed userId={} eventType={}: {}", userId, eventType, e.getMessage());
                saveLog(userId, NotificationChannel.PUSH, eventType, NotificationDeliveryStatus.FAILED, null, e.getMessage());
            }
        }
    }

    private void dispatchSms(UUID userId, String phoneNumber, String eventType, String body) {
        try {
            smsService.sendSms(phoneNumber, body);
            saveLog(userId, NotificationChannel.SMS, eventType, NotificationDeliveryStatus.SENT, null, null);
        } catch (Exception e) {
            log.warn("[SMS] Dispatch failed userId={} eventType={}: {}", userId, eventType, e.getMessage());
            saveLog(userId, NotificationChannel.SMS, eventType, NotificationDeliveryStatus.FAILED, null, e.getMessage());
        }
    }

    private void saveLog(UUID userId, NotificationChannel channel, String eventType,
                         NotificationDeliveryStatus status, String externalId, String errorMessage) {
        try {
            notificationLogRepository.save(new NotificationLog(
                    userId, channel, eventType, status, Instant.now(), externalId, errorMessage));
        } catch (Exception e) {
            log.warn("[NOTIFY] Failed to write NotificationLog: {}", e.getMessage());
        }
    }

    private UUID userId(NotificationEvent event) {
        return switch (event) {
            case NotificationEvent.OrderShipped e   -> e.userId();
            case NotificationEvent.OrderDelivered e -> e.userId();
            case NotificationEvent.OrderCancelled e -> e.userId();
            case NotificationEvent.BackInStock e    -> e.userId();
            case NotificationEvent.DeliverySlotConfirmed e   -> e.userId();
            case NotificationEvent.DeliverySlotUnavailable e -> e.userId();
        };
    }

    private String buildTitle(NotificationEvent event) {
        return switch (event) {
            case NotificationEvent.OrderShipped e   -> "Your order has shipped!";
            case NotificationEvent.OrderDelivered e -> "Your order has been delivered";
            case NotificationEvent.OrderCancelled e -> "Order cancelled";
            case NotificationEvent.BackInStock e    -> e.productName() + " is back in stock";
            case NotificationEvent.DeliverySlotConfirmed e   -> "Delivery slot confirmed";
            case NotificationEvent.DeliverySlotUnavailable e -> "Delivery slot unavailable";
        };
    }

    private String buildBody(NotificationEvent event) {
        return switch (event) {
            case NotificationEvent.OrderShipped e -> {
                String tracking = e.trackingNumber() != null ? " Tracking: " + e.trackingNumber() : "";
                yield "Hi " + e.firstName() + ", your order is on its way." + tracking;
            }
            case NotificationEvent.OrderDelivered e ->
                "Hi " + e.firstName() + ", your order has been delivered. Enjoy!";
            case NotificationEvent.OrderCancelled e ->
                "Hi " + e.firstName() + ", your order has been cancelled."
                    + (e.reason() != null ? " Reason: " + e.reason() : "");
            case NotificationEvent.BackInStock e ->
                e.productName()
                    + (e.variantTitle() != null ? " (" + e.variantTitle() + ")" : "")
                    + " is now available. Shop now: " + e.productUrl();
            case NotificationEvent.DeliverySlotConfirmed e ->
                "Hi " + e.firstName() + ", the seller confirmed your requested delivery slot"
                    + (e.date() != null ? " for " + e.date() : "") + ".";
            case NotificationEvent.DeliverySlotUnavailable e ->
                "Hi " + e.firstName() + ", the seller can't make your requested delivery slot."
                    + (e.reason() != null && !e.reason().isBlank() ? " Reason: " + e.reason() : "");
        };
    }
}
