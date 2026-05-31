package backend.services.impl.notification;

import backend.models.core.UserDevice;
import backend.repositories.UserDeviceRepository;
import backend.services.intf.notification.DeviceTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeviceTokenServiceImpl implements DeviceTokenService {

    private static final Logger log = LoggerFactory.getLogger(DeviceTokenServiceImpl.class);

    private final UserDeviceRepository userDeviceRepository;

    public DeviceTokenServiceImpl(UserDeviceRepository userDeviceRepository) {
        this.userDeviceRepository = userDeviceRepository;
    }

    @Override
    @Transactional
    public void registerToken(UUID userId, String platform, String token) {
        List<UserDevice> devices = userDeviceRepository.findByUserId(userId);
        boolean updated = false;
        for (UserDevice device : devices) {
            if ("IOS".equalsIgnoreCase(platform)) {
                if (token.equals(device.getApnsToken())) return; // already registered
                if (device.getApnsToken() == null && device.getFcmToken() == null) {
                    device.setApnsToken(token);
                    device.setTokenUpdatedAt(Instant.now());
                    userDeviceRepository.save(device);
                    updated = true;
                    break;
                }
            } else {
                // ANDROID or WEB — use FCM
                if (token.equals(device.getFcmToken())) return;
                if (device.getFcmToken() == null && device.getApnsToken() == null) {
                    device.setFcmToken(token);
                    device.setTokenUpdatedAt(Instant.now());
                    userDeviceRepository.save(device);
                    updated = true;
                    break;
                }
            }
        }
        if (!updated) {
            // Update the most-recently-seen device that has no token yet, or the first device
            UserDevice target = devices.stream()
                    .filter(d -> d.getFcmToken() == null && d.getApnsToken() == null)
                    .findFirst()
                    .orElseGet(() -> devices.isEmpty() ? null : devices.get(0));
            if (target == null) {
                log.warn("[DeviceToken] No device row found for userId={} to attach token", userId);
                return;
            }
            if ("IOS".equalsIgnoreCase(platform)) {
                target.setApnsToken(token);
            } else {
                target.setFcmToken(token);
            }
            target.setTokenUpdatedAt(Instant.now());
            userDeviceRepository.save(target);
        }
    }

    @Override
    @Transactional
    public void revokeToken(UUID userId, String token) {
        List<UserDevice> devices = userDeviceRepository.findByUserId(userId);
        for (UserDevice device : devices) {
            if (token.equals(device.getFcmToken())) {
                device.setFcmToken(null);
                device.setTokenUpdatedAt(Instant.now());
                userDeviceRepository.save(device);
                return;
            }
            if (token.equals(device.getApnsToken())) {
                device.setApnsToken(null);
                device.setTokenUpdatedAt(Instant.now());
                userDeviceRepository.save(device);
                return;
            }
        }
        log.debug("[DeviceToken] revokeToken: no device found with token for userId={}", userId);
    }
}
