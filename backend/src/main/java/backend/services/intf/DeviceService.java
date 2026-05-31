package backend.services.intf;

import backend.http.ClientInfo;
import backend.http.DeviceType;
import backend.models.core.UserDevice;

import java.util.List;

public interface DeviceService {

    /**
     * Payload extracted from a device verification token stored in Redis.
     */
    record DeviceVerificationPayload(
            long userId,
            String fingerprint,
            String browser,
            String os,
            DeviceType deviceType,
            String ip,
            String userAgent
    ) {}

    /**
     * Computes a stable device fingerprint as the SHA-256 hex of the user-agent string.
     */
    String computeFingerprint(String userAgent);

    /**
     * Returns true if the device identified by the fingerprint is already known for the given user.
     */
    boolean isKnownDevice(long userId, String fingerprint);

    /**
     * Upsert: if the device is already known, updates lastSeenAt and lastIp.
     * If it is new, inserts a new UserDevice row.
     */
    void recordDeviceSeen(long userId, ClientInfo clientInfo);

    /**
     * Generates a UUID token, stores device info in Redis with a short TTL,
     * and sends a verification email to the user.
     */
    void initiateDeviceVerification(long userId, String email, ClientInfo clientInfo);

    /**
     * Atomically consumes the device verification token from Redis and returns
     * the associated device payload. Throws BadRequestException if missing or malformed.
     */
    DeviceVerificationPayload consumeDeviceVerificationToken(String token);

    /**
     * Returns all registered devices for the given user.
     */
    List<UserDevice> getDevicesForUser(long userId);

    /**
     * Removes the device identified by deviceId, verifying it belongs to userId.
     * Throws ResourceNotFoundException if not found, ForbiddenException if not owned.
     */
    void removeDevice(long userId, long deviceId);
}
