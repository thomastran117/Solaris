package backend.services.impl.profile;

import backend.dtos.requests.notification.UpdateNotificationPreferencesRequest;
import backend.dtos.responses.notification.NotificationPreferencesResponse;
import backend.models.core.UserPreference;
import backend.repositories.UserPreferenceRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPreferenceServiceImplTest {

    private static final UUID USER_ID = TestIds.uuid(1);

    private UserPreferenceRepository repo;
    private UserPreferenceServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(UserPreferenceRepository.class);
        service = new UserPreferenceServiceImpl(repo);
    }

    @Test
    void isTrackingOptedOut_defaultsToFalseWhenMissing() {
        when(repo.findById(USER_ID)).thenReturn(Optional.empty());

        assertFalse(service.isTrackingOptedOut(USER_ID));
    }

    @Test
    void isTrackingOptedOut_usesCacheAfterFirstLookup() {
        UserPreference preference = new UserPreference(USER_ID);
        preference.setTrackingOptOut(true);
        when(repo.findById(USER_ID)).thenReturn(Optional.of(preference));

        assertTrue(service.isTrackingOptedOut(USER_ID));
        assertTrue(service.isTrackingOptedOut(USER_ID));

        verify(repo, times(1)).findById(USER_ID);
    }

    @Test
    void setTrackingOptOut_createsPreferenceWhenMissingAndUpdatesCache() {
        when(repo.findById(USER_ID)).thenReturn(Optional.empty());
        when(repo.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setTrackingOptOut(USER_ID, true);

        assertTrue(service.isTrackingOptedOut(USER_ID));
        verify(repo).save(any(UserPreference.class));
        verify(repo, times(1)).findById(USER_ID);
    }

    @Test
    void setTrackingOptOut_updatesExistingPreferenceAndSubsequentReadUsesCache() {
        UserPreference preference = new UserPreference(USER_ID);
        preference.setTrackingOptOut(false);
        when(repo.findById(USER_ID)).thenReturn(Optional.of(preference));
        when(repo.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setTrackingOptOut(USER_ID, true);

        assertTrue(preference.isTrackingOptOut());
        assertTrue(service.isTrackingOptedOut(USER_ID));
        verify(repo).save(preference);
        verify(repo, times(1)).findById(USER_ID);
    }

    // ── getNotificationPreferences ────────────────────────────────────────────

    @Test
    void getNotificationPreferences_existingPref_returnsMappedValues() {
        UserPreference pref = new UserPreference(USER_ID);
        pref.setPushEnabled(true);
        pref.setSmsEnabled(false);
        pref.setSmsPhoneNumber("+1 555 0100");
        when(repo.findById(USER_ID)).thenReturn(Optional.of(pref));

        NotificationPreferencesResponse response = service.getNotificationPreferences(USER_ID);

        assertTrue(response.pushEnabled());
        assertFalse(response.smsEnabled());
        assertEquals("+1 555 0100", response.smsPhoneNumber());
    }

    @Test
    void getNotificationPreferences_noExistingPref_returnsDefaults() {
        when(repo.findById(USER_ID)).thenReturn(Optional.empty());

        NotificationPreferencesResponse response = service.getNotificationPreferences(USER_ID);

        assertNotNull(response);
    }

    // ── updateNotificationPreferences ─────────────────────────────────────────

    @Test
    void updateNotificationPreferences_allNonNull_updatesAll() {
        UserPreference pref = new UserPreference(USER_ID);
        when(repo.findById(USER_ID)).thenReturn(Optional.of(pref));
        when(repo.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new UpdateNotificationPreferencesRequest(true, true, "+1 555 0200");
        NotificationPreferencesResponse response = service.updateNotificationPreferences(USER_ID, request);

        assertTrue(pref.isPushEnabled());
        assertTrue(pref.isSmsEnabled());
        assertEquals("+1 555 0200", pref.getSmsPhoneNumber());
        verify(repo).save(pref);
    }

    @Test
    void updateNotificationPreferences_blankPhone_setsNull() {
        UserPreference pref = new UserPreference(USER_ID);
        pref.setSmsPhoneNumber("+1 555 9999");
        when(repo.findById(USER_ID)).thenReturn(Optional.of(pref));
        when(repo.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new UpdateNotificationPreferencesRequest(null, null, "   ");
        service.updateNotificationPreferences(USER_ID, request);

        assertNull(pref.getSmsPhoneNumber());
    }

    @Test
    void updateNotificationPreferences_nullFields_doesNotMutate() {
        UserPreference pref = new UserPreference(USER_ID);
        pref.setPushEnabled(false);
        pref.setSmsEnabled(true);
        pref.setSmsPhoneNumber("+1 555 0001");
        when(repo.findById(USER_ID)).thenReturn(Optional.of(pref));
        when(repo.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new UpdateNotificationPreferencesRequest(null, null, null);
        service.updateNotificationPreferences(USER_ID, request);

        assertFalse(pref.isPushEnabled());
        assertTrue(pref.isSmsEnabled());
        assertEquals("+1 555 0001", pref.getSmsPhoneNumber());
    }

    @Test
    void updateNotificationPreferences_createsNewPrefWhenMissing() {
        when(repo.findById(USER_ID)).thenReturn(Optional.empty());
        when(repo.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new UpdateNotificationPreferencesRequest(true, false, null);
        NotificationPreferencesResponse response = service.updateNotificationPreferences(USER_ID, request);

        verify(repo).save(any(UserPreference.class));
        assertNotNull(response);
    }
}
