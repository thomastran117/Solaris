package backend.dtos.responses.risk;

import backend.models.enums.RiskDecision;
import backend.models.enums.RiskSignalType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row of {@link RiskAssessmentResponse#getSignals()} — the per-family verdict. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RiskSignalResponse {
    private RiskSignalType type;
    private RiskDecision decision;
    private int scoreContribution;
    private String reason;
}
