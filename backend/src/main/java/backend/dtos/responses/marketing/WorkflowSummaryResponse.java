package backend.dtos.responses.marketing;

import backend.models.core.MarketingWorkflow;
import backend.models.enums.WorkflowActionType;
import backend.models.enums.WorkflowStatus;
import backend.models.enums.WorkflowTrigger;

import java.time.Instant;
import java.util.UUID;

public record WorkflowSummaryResponse(
        UUID id,
        UUID companyId,
        String name,
        WorkflowTrigger trigger,
        int delayHours,
        UUID targetSegmentId,
        WorkflowActionType actionType,
        String emailSubject,
        int cooldownDays,
        WorkflowStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkflowSummaryResponse from(MarketingWorkflow w) {
        return new WorkflowSummaryResponse(
                w.getId(), w.getCompanyId(), w.getName(), w.getTrigger(),
                w.getDelayHours(), w.getTargetSegmentId(), w.getActionType(),
                w.getEmailSubject(), w.getCooldownDays(),
                w.getStatus(), w.getCreatedAt(), w.getUpdatedAt()
        );
    }
}
