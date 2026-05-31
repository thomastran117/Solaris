package backend.services.intf.profile;

import java.util.UUID;
import backend.dtos.responses.profile.ProfileResponse;

public interface ProfileService {
    ProfileResponse getProfile(UUID userId);

    ProfileResponse updateProfile(UUID userId, String firstName, String lastName, String phoneNumber, String address);
}
