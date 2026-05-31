package backend.dtos.responses.support;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class TicketMessageResponse {
    private UUID id;
    private UUID authorId;
    private String authorName;
    private String authorRole;
    private String body;
    private Instant createdAt;
}
