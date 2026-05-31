package backend.dtos.requests.collection;

import backend.annotations.safeIdentifier.SafeIdentifier;
import backend.annotations.safeRichText.SafeRichText;
import backend.annotations.safeText.SafeText;
import backend.models.enums.CollectionStatus;
import backend.models.enums.CollectionType;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Partial update — any null field is left untouched. Mirrors the convention used by
 * {@code UpdateProductRequest}.
 */
@Getter
@Setter
public class UpdateCollectionRequest {

    @Size(max = 200)
    @SafeText
    private String name;

    @Size(max = 200)
    @SafeIdentifier
    private String slug;

    @Size(max = 10000)
    @SafeRichText
    private String description;

    @Size(max = 500)
    private String imageUrl;

    /** Switching type triggers a re-materialisation on save. */
    private CollectionType type;

    private CollectionStatus status;

    private Boolean featured;

    private Integer featuredRank;

    @Size(max = 10000)
    private String rulesJson;
}
