package backend.services.impl.profile;

import backend.models.core.UserPreference;
import backend.repositories.UserPreferenceRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
