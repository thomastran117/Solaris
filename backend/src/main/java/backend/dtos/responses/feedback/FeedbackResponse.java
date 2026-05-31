package backend.dtos.responses.feedback;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class FeedbackResponse {
    private UUID id;
    private UUID submittedById;
    private String submitterEmail;
    private String submitterName;
    private String category;
    private String status;
    private String message;
    private Integer rating;
    private String pageContext;
    private Instant reviewedAt;
    private UUID reviewedById;
    private Instant createdAt;
    private Instant updatedAt;
}
