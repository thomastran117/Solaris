package backend.dtos.requests.savedlist;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AddSavedListItemRequest {

    @NotNull
    private Long productId;

    private Long variantId;

    @Min(1)
    private Integer quantity = 1;

    @SafeText
    @Size(max = 500)
    private String note;
}
