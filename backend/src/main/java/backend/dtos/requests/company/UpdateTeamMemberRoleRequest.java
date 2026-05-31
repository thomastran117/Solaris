package backend.dtos.requests.company;

import backend.models.enums.CompanyRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateTeamMemberRoleRequest {

    @NotNull
    private CompanyRole role;
}
