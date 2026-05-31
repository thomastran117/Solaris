package backend.dtos.responses.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class KitSlotResponse {
    private UUID id;
    private String name;
    private String description;
    private boolean required;
    private int minQty;
    private int maxQty;
    private int displayOrder;
    private List<KitSlotChoiceResponse> choices;
}
