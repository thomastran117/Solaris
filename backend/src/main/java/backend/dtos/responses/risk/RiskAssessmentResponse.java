package backend.dtos.responses.risk;

import java.util.UUID;
import backend.models.enums.RiskAction;
import backend.models.enums.RiskAssessmentKind;
import backend.models.enums.RiskMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/** The stored risk assessment for an order, exposed to merchants via the review queue. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessmentResponse {
    private UUID id;
    private UUID orderId;
    private UUID userId;
    private RiskAction decision;
    private int score;
    private RiskMode mode;
    private RiskAssessmentKind kind;
    private String ip;
    private String deviceFingerprint;
    private String userAgent;
    private Instant createdAt;
    private List<RiskSignalResponse> signals;
}
