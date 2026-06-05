package backend.services.impl.profile;

import backend.dtos.responses.profile.ProfileResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.User;
import backend.repositories.UserRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceImplTest {

    private static final UUID USER_ID = TestIds.uuid(1);

    private UserRepository userRepository;
    private ProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new ProfileServiceImpl(userRepository);
    }

    @Test
    void getProfile_mapsUserToResponse() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));

        ProfileResponse response = service.getProfile(USER_ID);

        assertEquals(USER_ID, response.getId());
        assertEquals("alex@example.com", response.getEmail());
        assertEquals("Alex", response.getFirstName());
        assertEquals("Morgan", response.getLastName());
    }

    @Test
    void getProfile_missingUserThrowsNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getProfile(USER_ID));
    }

    @Test
    void updateProfile_changesOnlyNonNullFieldsAndSaves() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse response = service.updateProfile(
                USER_ID,
                "Jamie",
                null,
                "+1 555 0199",
                null
        );

        verify(userRepository).save(user);
        assertEquals("Jamie", user.getFirstName());
        assertEquals("Morgan", user.getLastName());
        assertEquals("+1 555 0199", user.getPhoneNumber());
        assertEquals("123 King St", user.getAddress());
        assertEquals("Jamie", response.getFirstName());
        assertEquals("Morgan", response.getLastName());
    }

    @Test
    void updateProfile_allFieldsNonNull_updatesAll() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateProfile(USER_ID, "Sam", "Lee", "+1 555 7777", "99 Oak Ave");

        assertEquals("Sam", user.getFirstName());
        assertEquals("Lee", user.getLastName());
        assertEquals("+1 555 7777", user.getPhoneNumber());
        assertEquals("99 Oak Ave", user.getAddress());
    }

    @Test
    void updateProfile_missingUserThrowsNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateProfile(USER_ID, "Alex", "Morgan", null, null));
    }

    private User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("alex@example.com");
        user.setFirstName("Alex");
        user.setLastName("Morgan");
        user.setPhoneNumber("+1 555 0101");
        user.setAddress("123 King St");
        return user;
    }
}
