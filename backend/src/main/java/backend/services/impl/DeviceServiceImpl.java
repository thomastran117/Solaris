package backend.services.impl;

import backend.configurations.environment.EnvironmentSetting;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.http.ClientInfo;
import backend.http.DeviceType;
import backend.models.core.UserDevice;
import backend.repositories.UserDeviceRepository;
import backend.repositories.UserRepository;
import backend.services.intf.CacheService;
import backend.services.intf.DeviceService;
import backend.services.intf.EmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeviceServiceImpl implements DeviceService {

    private static final String DEVICE_VERIFY_PREFIX = "device:verify:";

    private final UserDeviceRepository userDeviceRepository;
    private final UserRepository userRepository;
    private final CacheService cache;
    private final EmailService emailService;
    private final EnvironmentSetting env;

    public DeviceServiceImpl(UserDeviceRepository userDeviceRepository,
                             UserRepository userRepository,
                             CacheService cache,
                             EmailService emailService,
                             EnvironmentSetting env) {
        this.userDeviceRepository = userDeviceRepository;
        this.userRepository = userRepository;
        this.cache = cache;
        this.emailService = emailService;
        this.env = env;
    }

    @Override
    public String computeFingerprint(String userAgent) {
        String input = userAgent == null ? "" : userAgent;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public boolean isKnownDevice(long userId, String fingerprint) {
        return userDeviceRepository.findByUserIdAndFingerprint(userId, fingerprint).isPresent();
    }

    @Override
    @Transactional
    public void recordDeviceSeen(long userId, ClientInfo clientInfo) {
        String fingerprint = computeFingerprint(clientInfo.userAgent());
        Optional<UserDevice> existing = userDeviceRepository.findByUserIdAndFingerprint(userId, fingerprint);
        if (existing.isPresent()) {
            UserDevice device = existing.get();
            device.setLastSeenAt(Instant.now());
            device.setLastIp(clientInfo.ip());
            userDeviceRepository.save(device);
        } else {
            UserDevice device = new UserDevice();
            device.setUser(userRepository.getReferenceById(userId));
            device.setFingerprint(fingerprint);
            device.setDeviceType(clientInfo.deviceType());
            device.setBrowser(clientInfo.browser());
            device.setOs(clientInfo.os());
            device.setUserAgent(clientInfo.userAgent());
            device.setLastIp(clientInfo.ip());
            device.setLastSeenAt(Instant.now());
            userDeviceRepository.save(device);
        }
    }

    @Override
    public void initiateDeviceVerification(long userId, String email, ClientInfo clientInfo) {
        String fingerprint = computeFingerprint(clientInfo.userAgent());
        String payload = userId + "|" +
                fingerprint + "|" +
                clientInfo.browser() + "|" +
                clientInfo.os() + "|" +
                clientInfo.deviceType().name() + "|" +
                clientInfo.ip() + "|" +
                clientInfo.userAgent();

        String token = UUID.randomUUID().toString();
        long ttl = env.getEmail().getDeviceVerificationTokenTtlSeconds();
        cache.set(DEVICE_VERIFY_PREFIX + token, payload, ttl);
        emailService.sendDeviceVerificationEmail(email, token,
                clientInfo.browser(), clientInfo.os(), clientInfo.ip());
    }

    @Override
    public DeviceVerificationPayload consumeDeviceVerificationToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Device verification token is required.");
        }
        String raw = cache.getAndDelete(DEVICE_VERIFY_PREFIX + token);
        if (raw == null) {
            throw new BadRequestException("Invalid or expired device verification token.");
        }
        String[] parts = raw.split("\\|", 7);
        if (parts.length != 7) {
            throw new BadRequestException("Malformed device verification token.");
        }
        long userId;
        try {
            userId = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Malformed device verification token.");
        }
        DeviceType deviceType;
        try {
            deviceType = DeviceType.valueOf(parts[4]);
        } catch (IllegalArgumentException e) {
            deviceType = DeviceType.UNKNOWN;
        }
        return new DeviceVerificationPayload(userId, parts[1], parts[2], parts[3], deviceType, parts[5], parts[6]);
    }

    @Override
    public List<UserDevice> getDevicesForUser(long userId) {
        return userDeviceRepository.findByUserId(userId);
    }

    @Override
    @Transactional
    public void removeDevice(long userId, long deviceId) {
        UserDevice device = userDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found."));
        if (device.getUser().getId() != userId) {
            throw new ForbiddenException("You do not have permission to remove this device.");
        }
        userDeviceRepository.deleteById(deviceId);
    }
}
