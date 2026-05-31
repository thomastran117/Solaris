package backend.services.impl.notification;

import backend.services.intf.notification.PushNotificationService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FcmPushNotificationService implements PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushNotificationService.class);

    @Override
    public void sendPush(String token, String title, String body, Map<String, String> data) {
        Message.Builder builder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());
        if (data != null) {
            data.forEach(builder::putData);
        }
        try {
            String messageId = FirebaseMessaging.getInstance().send(builder.build());
            log.info("[PUSH] Sent to token={} messageId={}", token, messageId);
        } catch (Exception e) {
            throw new RuntimeException("FCM send failed for token=" + token + ": " + e.getMessage(), e);
        }
    }
}
