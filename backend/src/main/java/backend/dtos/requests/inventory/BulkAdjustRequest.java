package backend.dtos.requests.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkAdjustRequest {

    @NotEmpty(message = "Items list must not be empty")
    @Size(max = 50, message = "Bulk adjust is limited to 50 items per request")
    @Valid
    private List<BulkAdjustItem> items;
}
