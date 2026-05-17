package backend.dtos.responses.review;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReviewMediaResponse {
    private Long id;
    private String url;
    private int position;
}
