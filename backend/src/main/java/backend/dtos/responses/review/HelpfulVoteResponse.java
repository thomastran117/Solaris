package backend.dtos.responses.review;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class HelpfulVoteResponse {
    private UUID reviewId;
    private int helpfulCount;
    private boolean userHasVoted;
}
