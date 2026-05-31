package backend.dtos.requests.savedlist;

import backend.annotations.safeRichText.SafeRichText;
import backend.annotations.safeText.SafeText;
import backend.models.core.SavedListType;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateSavedListRequest {

    @SafeText
    @Size(max = 100)
    private String name;

    private SavedListType type;

    @SafeRichText
    @Size(max = 500)
    private String description;

    private Boolean isPublic;
}
