package backend.dtos.requests.product;

import backend.models.enums.ProductRelationshipType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AddProductRelationshipRequest {

    @NotNull(message = "Target product ID is required")
    private UUID targetProductId;

    @NotNull(message = "Relationship type is required")
    private ProductRelationshipType type;

    @Size(max = 500, message = "Note must not exceed 500 characters")
    private String note;

    private int displayOrder = 0;
}
