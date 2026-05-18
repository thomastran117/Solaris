package backend.dtos.responses.review;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ReviewMediaResponse {
    private UUID id;
    private String url;
    private int position;
}
