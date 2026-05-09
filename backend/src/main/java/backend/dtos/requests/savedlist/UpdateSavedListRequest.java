package backend.dtos.requests.savedlist;

import backend.models.core.SavedListType;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateSavedListRequest {

    @Size(max = 100)
    private String name;

    private SavedListType type;

    @Size(max = 500)
    private String description;

    private Boolean isPublic;
}
