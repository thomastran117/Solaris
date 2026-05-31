package backend.services.intf;

import backend.dtos.responses.profile.ProfileResponse;

public interface ProfileService {
    ProfileResponse getProfile(long userId);

    ProfileResponse updateProfile(long userId, String firstName, String lastName, String phoneNumber, String address);
}
