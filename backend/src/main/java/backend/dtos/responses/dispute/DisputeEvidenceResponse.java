package backend.dtos.responses.dispute;

import backend.models.core.DisputeEvidence;
import backend.models.enums.DisputeEvidenceType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DisputeEvidenceResponse {
    private UUID id;
    private DisputeEvidenceType evidenceType;
    private String content;
    private String attachmentUrl;
    /** Null for entries generated automatically at case creation. */
    private UUID createdById;
    private Instant createdAt;

    public static DisputeEvidenceResponse from(DisputeEvidence e) {
        return new DisputeEvidenceResponse(
                e.getId(),
                e.getEvidenceType(),
                e.getContent(),
                e.getAttachmentUrl(),
                e.getCreatedById(),
                e.getCreatedAt());
    }
}
