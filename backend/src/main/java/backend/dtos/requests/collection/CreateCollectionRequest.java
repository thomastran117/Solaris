package backend.dtos.requests.collection;

import backend.annotations.safeIdentifier.SafeIdentifier;
import backend.annotations.safeRichText.SafeRichText;
import backend.annotations.safeText.SafeText;
import backend.models.enums.CollectionStatus;
import backend.models.enums.CollectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCollectionRequest {

    @NotBlank(message = "Collection name is required")
    @Size(max = 200)
    @SafeText
    private String name;

    @NotBlank(message = "Slug is required")
    @Size(max = 200)
    @SafeIdentifier
    private String slug;

    @Size(max = 10000)
    @SafeRichText
    private String description;

    @Size(max = 500)
    private String imageUrl;

    @NotNull
    private CollectionType type = CollectionType.STATIC;

    private CollectionStatus status;

    private boolean featured = false;

    private Integer featuredRank;

    /**
     * JSON body describing tag/category/brand rules. Required when {@link #type} is DYNAMIC.
     * Schema: {@code { "tagsAnyOf": [...], "categoriesAnyOf": [...], "brandsAnyOf": [...] }}.
     */
    @Size(max = 10000)
    private String rulesJson;
}
