package backend.services.intf.profile;

import backend.dtos.requests.notification.UpdateNotificationPreferencesRequest;
import backend.dtos.responses.notification.NotificationPreferencesResponse;

import java.util.UUID;

public interface UserPreferenceService {
    boolean isTrackingOptedOut(UUID userId);
    void setTrackingOptOut(UUID userId, boolean optOut);
    NotificationPreferencesResponse getNotificationPreferences(UUID userId);
    NotificationPreferencesResponse updateNotificationPreferences(UUID userId, UpdateNotificationPreferencesRequest request);
}
