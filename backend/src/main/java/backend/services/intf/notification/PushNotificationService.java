package backend.services.intf.notification;

import java.util.Map;

public interface PushNotificationService {
    void sendPush(String token, String title, String body, Map<String, String> data);
}
