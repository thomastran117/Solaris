package backend.dtos.requests.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class KitSlotRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    private boolean required = true;

    @Min(1)
    @Max(99)
    private int minQty = 1;

    @Min(1)
    @Max(99)
    private int maxQty = 1;

    private int displayOrder = 0;

    @Valid
    private List<KitSlotChoiceRequest> choices = new ArrayList<>();
}
