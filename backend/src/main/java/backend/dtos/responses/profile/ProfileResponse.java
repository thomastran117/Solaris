package backend.dtos.responses.profile;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ProfileResponse {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String address;
    private String tier;
}
