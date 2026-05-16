package backend.dtos.requests.savedlist;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateSavedListItemRequest {

    @Min(1)
    private Integer quantity;

    @SafeText
    @Size(max = 500)
    private String note;

    private Boolean purchased;
}
