package backend.dtos.responses.company;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class AnnouncementResponse {
    private UUID id;
    private UUID companyId;
    private String companyName;
    private String companyLogoUrl;
    private String title;
    private String body;
    private String type;
    private String status;
    private UUID productId;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
