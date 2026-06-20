package backend.dtos.requests.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ReceiveItemsRequest {

    @NotEmpty
    @Valid
    private List<ReceiveLineItem> items;
}
