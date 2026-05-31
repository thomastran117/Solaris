package backend.services.intf.notification;

import java.util.UUID;

public interface DeviceTokenService {
    void registerToken(UUID userId, String platform, String token);
    void revokeToken(UUID userId, String token);
}
