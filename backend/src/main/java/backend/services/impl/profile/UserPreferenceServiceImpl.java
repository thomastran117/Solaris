package backend.services.impl.profile;

import java.util.UUID;
import backend.dtos.requests.notification.UpdateNotificationPreferencesRequest;
import backend.dtos.responses.notification.NotificationPreferencesResponse;
import backend.models.core.UserPreference;
import backend.repositories.UserPreferenceRepository;
import backend.services.intf.profile.UserPreferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserPreferenceServiceImpl implements UserPreferenceService {

    private final UserPreferenceRepository repo;

    // Simple in-memory cache: userId -> trackingOptOut.
    // Invalidated on write, so no TTL is needed — only the setTrackingOptOut
    // endpoint mutates this and it also evicts the entry.
    private final ConcurrentHashMap<java.util.UUID, Boolean> cache = new ConcurrentHashMap<>();

    public UserPreferenceServiceImpl(UserPreferenceRepository repo) {
        this.repo = repo;
    }

    @Override
    public boolean isTrackingOptedOut(UUID userId) {
        return cache.computeIfAbsent(userId, id ->
            repo.findById(id).map(UserPreference::isTrackingOptOut).orElse(false)
        );
    }

    @Override
    @Transactional
    public void setTrackingOptOut(UUID userId, boolean optOut) {
        UserPreference pref = repo.findById(userId).orElseGet(() -> new UserPreference(userId));
        pref.setTrackingOptOut(optOut);
        repo.save(pref);
        cache.put(userId, optOut);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getNotificationPreferences(UUID userId) {
        UserPreference pref = repo.findById(userId).orElseGet(() -> new UserPreference(userId));
        return new NotificationPreferencesResponse(pref.isPushEnabled(), pref.isSmsEnabled(), pref.getSmsPhoneNumber());
    }

    @Override
    @Transactional
    public NotificationPreferencesResponse updateNotificationPreferences(UUID userId, UpdateNotificationPreferencesRequest request) {
        UserPreference pref = repo.findById(userId).orElseGet(() -> new UserPreference(userId));
        if (request.pushEnabled() != null) pref.setPushEnabled(request.pushEnabled());
        if (request.smsEnabled() != null) pref.setSmsEnabled(request.smsEnabled());
        if (request.smsPhoneNumber() != null) pref.setSmsPhoneNumber(request.smsPhoneNumber().isBlank() ? null : request.smsPhoneNumber());
        repo.save(pref);
        return new NotificationPreferencesResponse(pref.isPushEnabled(), pref.isSmsEnabled(), pref.getSmsPhoneNumber());
    }
}
