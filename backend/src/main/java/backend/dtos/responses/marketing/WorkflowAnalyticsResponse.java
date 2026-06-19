package backend.dtos.responses.marketing;

import java.util.UUID;

public record WorkflowAnalyticsResponse(
        UUID workflowId,
        long enrolledCount,
        long sentCount
) {}
