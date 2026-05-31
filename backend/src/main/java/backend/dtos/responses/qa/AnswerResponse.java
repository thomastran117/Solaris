package backend.dtos.responses.qa;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class AnswerResponse {
    private UUID id;
    private UUID questionId;
    private UUID answeredById;
    private String answererFirstName;
    private String answererLastName;
    private String answerText;
    private boolean vendorAnswer;
    private int upvoteCount;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
