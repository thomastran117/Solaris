package backend.dtos.requests.imports;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AttachImagesRequest {

    @NotEmpty(message = "items must not be empty")
    @Size(max = 200, message = "Attach is limited to 200 items per request")
    @Valid
    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        @NotBlank
        @Size(max = 100)
        private String sku;

        @NotBlank
        @Size(max = 500)
        private String imageUrl;

        @NotNull
        @Min(0)
        private Integer displayOrder;
    }
}
