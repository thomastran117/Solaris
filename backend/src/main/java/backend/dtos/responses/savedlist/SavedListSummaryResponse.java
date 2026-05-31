package backend.dtos.responses.savedlist;

import java.util.UUID;
import backend.models.core.SavedListType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class SavedListSummaryResponse {
    private UUID id;
    private UUID userId;
    private String name;
    private SavedListType type;
    private String description;
    private boolean isPublic;
    private String shareSlug;
    private int itemCount;
    private int purchasedCount;
    private Instant createdAt;
    private Instant updatedAt;
}
