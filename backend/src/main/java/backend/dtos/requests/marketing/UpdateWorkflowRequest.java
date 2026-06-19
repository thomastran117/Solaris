package backend.dtos.requests.marketing;

import backend.models.enums.WorkflowStatus;
import jakarta.validation.constraints.Size;

public record UpdateWorkflowRequest(
        WorkflowStatus status,
        @Size(max = 255) String name,
        @Size(max = 255) String emailSubject,
        String emailBody
) {}
