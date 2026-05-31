package backend.dtos.requests.company;

import backend.models.enums.CompanyRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InviteTeamMemberRequest {

    @NotBlank
    @Email
    private String email;

    @NotNull
    private CompanyRole role;
}
