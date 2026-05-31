package backend.dtos.requests.product;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ImportFromCollectionRequest {

    @NotNull
    private UUID collectionId;

    @NotNull
    private UUID slotId;
}
