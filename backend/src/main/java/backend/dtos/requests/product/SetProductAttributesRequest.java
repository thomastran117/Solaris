package backend.dtos.requests.product;

import backend.annotations.safeText.SafeText;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SetProductAttributesRequest {

    @NotNull
    @Valid
    private List<AttributeItem> attributes;

    @Getter
    @Setter
    public static class AttributeItem {

        @NotBlank(message = "Attribute name is required")
        @Size(max = 100, message = "Attribute name must be at most 100 characters")
        @SafeText
        private String name;

        @NotBlank(message = "Attribute value is required")
        @Size(max = 500, message = "Attribute value must be at most 500 characters")
        @SafeText
        private String value;

        private int displayOrder = 0;
    }
}
