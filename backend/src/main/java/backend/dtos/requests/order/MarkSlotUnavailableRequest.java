package backend.dtos.requests.order;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Vendor request to flag a requested delivery slot as unavailable. The optional reason
 * is surfaced to the customer in the notification email.
 */
@Getter
@Setter
public class MarkSlotUnavailableRequest {

    @SafeText
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}
