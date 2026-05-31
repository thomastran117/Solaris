package backend.dtos.requests.company;

import backend.models.enums.AnnouncementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateAnnouncementRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    private String body;

    @NotNull
    private AnnouncementType type;

    private UUID productId;
}
