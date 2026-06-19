package backend.dtos.requests.marketing;

import backend.models.enums.WorkflowActionType;
import backend.models.enums.WorkflowTrigger;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateWorkflowRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull WorkflowTrigger trigger,
        @Min(0) int delayHours,
        UUID targetSegmentId,
        @NotNull WorkflowActionType actionType,
        @Size(max = 255) String emailSubject,
        String emailBody,
        @Min(0) int cooldownDays
) {}
