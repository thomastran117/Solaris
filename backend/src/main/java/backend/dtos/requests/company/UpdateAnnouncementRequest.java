package backend.dtos.requests.company;

import backend.models.enums.AnnouncementType;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateAnnouncementRequest {

    @Size(max = 200)
    private String title;

    private String body;

    private AnnouncementType type;

    private UUID productId;

    private Boolean publish;
}
