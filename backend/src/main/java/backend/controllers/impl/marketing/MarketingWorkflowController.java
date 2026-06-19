package backend.controllers.impl.marketing;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.marketing.CreateWorkflowRequest;
import backend.dtos.requests.marketing.UpdateWorkflowRequest;
import backend.dtos.responses.marketing.WorkflowAnalyticsResponse;
import backend.dtos.responses.marketing.WorkflowResponse;
import backend.dtos.responses.marketing.WorkflowSummaryResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.marketing.MarketingWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
public class MarketingWorkflowController {

    private final MarketingWorkflowService workflowService;

    public MarketingWorkflowController(MarketingWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping("/companies/{companyId}/marketing/workflows")
    @RequireAuth
    public ResponseEntity<WorkflowResponse> create(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateWorkflowRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(workflowService.createWorkflow(companyId, resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/companies/{companyId}/marketing/workflows")
    @RequireAuth
    public ResponseEntity<List<WorkflowSummaryResponse>> list(@PathVariable UUID companyId) {
        try {
            return ResponseEntity.ok(workflowService.getWorkflows(companyId, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/companies/{companyId}/marketing/workflows/{workflowId}")
    @RequireAuth
    public ResponseEntity<WorkflowResponse> update(
            @PathVariable UUID companyId,
            @PathVariable UUID workflowId,
            @Valid @RequestBody UpdateWorkflowRequest request) {
        try {
            return ResponseEntity.ok(
                    workflowService.updateWorkflow(companyId, workflowId, resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/companies/{companyId}/marketing/workflows/{workflowId}/analytics")
    @RequireAuth
    public ResponseEntity<WorkflowAnalyticsResponse> analytics(
            @PathVariable UUID companyId,
            @PathVariable UUID workflowId) {
        try {
            return ResponseEntity.ok(
                    workflowService.getAnalytics(companyId, workflowId, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
