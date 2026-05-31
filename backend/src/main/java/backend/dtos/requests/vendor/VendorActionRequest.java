package backend.dtos.requests.vendor;

import backend.annotations.safeText.SafeText;
import backend.models.enums.VendorTier;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VendorActionRequest {

    /** Optional reason — required for reject and suspend actions. */
    @SafeText
    @Size(max = 1000, message = "Reason must not exceed 1000 characters")
    private String reason;

    /** Optional tier override applied when approving a vendor. */
    private VendorTier tier;
}
