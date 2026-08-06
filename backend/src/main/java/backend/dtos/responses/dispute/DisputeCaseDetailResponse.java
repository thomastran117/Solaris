package backend.dtos.responses.dispute;

import backend.models.core.DisputeCase;
import backend.models.core.DisputeEvidence;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DisputeCaseDetailResponse {
    private DisputeCaseResponse dispute;
    private List<DisputeEvidenceResponse> evidence;

    public static DisputeCaseDetailResponse from(DisputeCase c, List<DisputeEvidence> evidence) {
        return new DisputeCaseDetailResponse(
                DisputeCaseResponse.from(c, evidence.size()),
                evidence.stream().map(DisputeEvidenceResponse::from).toList());
    }
}
