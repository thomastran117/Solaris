package backend.dtos.responses.note;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class InternalNoteResponse {
    private UUID id;
    private String entityType;
    private UUID entityId;
    private UUID authorId;
    private String authorName;
    private String body;
    private Instant createdAt;
}
