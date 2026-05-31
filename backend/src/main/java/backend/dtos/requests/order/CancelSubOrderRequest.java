package backend.dtos.requests.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelSubOrderRequest {

    @NotBlank(message = "Cancellation reason is required")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
