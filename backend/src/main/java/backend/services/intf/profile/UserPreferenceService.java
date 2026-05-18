package backend.services.intf.profile;

import java.util.UUID;

public interface UserPreferenceService {
    boolean isTrackingOptedOut(UUID userId);
    void setTrackingOptOut(UUID userId, boolean optOut);
}
