package backend.dtos.responses.review;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HelpfulVoteResponse {
    private Long reviewId;
    private int helpfulCount;
    private boolean userHasVoted;
}
