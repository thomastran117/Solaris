package backend.dtos.requests.inventory;

import backend.annotations.safeRichText.SafeRichText;
import backend.models.enums.RestockStatus;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateRestockRequest {

    private LocalDate expectedArrivalDate;

    @Size(max = 2000)
    @SafeRichText
    private String supplierNote;

    /** Target status transition. Required when transitioning to RECEIVED. */
    private RestockStatus status;

    /** Required when status == RECEIVED. Must be >= 1. */
    private Integer receivedQty;
}
