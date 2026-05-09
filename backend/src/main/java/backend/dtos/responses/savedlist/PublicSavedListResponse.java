package backend.dtos.responses.savedlist;

import backend.models.core.SavedListType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class PublicSavedListResponse {
    private Long id;
    private String ownerDisplayName;
    private String name;
    private SavedListType type;
    private String description;
    private String shareSlug;
    private List<SavedListItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;
}
