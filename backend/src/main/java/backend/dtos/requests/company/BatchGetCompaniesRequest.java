package backend.dtos.requests.company;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BatchGetCompaniesRequest {

    @NotEmpty(message = "At least one company ID is required")
    @Size(max = 100, message = "Cannot request more than 100 companies at once")
    private List<Long> ids;
}
