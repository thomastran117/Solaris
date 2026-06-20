package backend.dtos.requests.inventory;

import backend.annotations.safeRichText.SafeRichText;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreatePORequest {

    @NotNull
    private UUID supplierId;

    private LocalDate expectedArrivalDate;

    @Size(max = 2000)
    @SafeRichText
    private String notes;

    private UUID restockRequestId;

    @NotEmpty
    @Valid
    private List<POLineItemRequest> items;
}
