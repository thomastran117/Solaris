package backend.dtos.responses.qa;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class QuestionResponse {
    private UUID id;
    private UUID productId;
    private UUID askedById;
    private String askerFirstName;
    private String askerLastName;
    private String questionText;
    private String status;
    private List<AnswerResponse> answers;
    private Instant createdAt;
    private Instant updatedAt;
}
