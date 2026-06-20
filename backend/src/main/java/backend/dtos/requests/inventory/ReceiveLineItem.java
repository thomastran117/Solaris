package backend.dtos.requests.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ReceiveLineItem {

    @NotNull
    private UUID itemId;

    @NotNull
    @Min(1)
    private Integer receivedQty;
}
