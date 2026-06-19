package backend.dtos.responses.marketing;

import org.springframework.data.domain.Page;

import java.util.List;

public record WorkflowListResponse(
        List<WorkflowSummaryResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size,
        boolean first,
        boolean last
) {
    public static WorkflowListResponse from(Page<WorkflowSummaryResponse> p) {
        return new WorkflowListResponse(
                p.getContent(), p.getTotalElements(), p.getTotalPages(),
                p.getNumber(), p.getSize(), p.isFirst(), p.isLast());
    }
}
