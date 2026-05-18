package backend.controllers.impl.orders;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.issue.OpenIssueRequest;
import backend.dtos.requests.issue.RejectIssueRequest;
import backend.dtos.requests.issue.ResolveWithCreditRequest;
import backend.dtos.requests.issue.ResolveWithRefundRequest;
import backend.dtos.requests.issue.ResolveWithReplacementRequest;
import backend.dtos.requests.issue.TransitionIssueRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.issue.OrderIssueResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.models.enums.OrderIssueState;
import backend.services.intf.orders.OrderIssueService;

import java.util.List;

@RestController
public class OrderIssueController {

    private final OrderIssueService issueService;

    public OrderIssueController(OrderIssueService issueService) {
        this.issueService = issueService;
    }

    @PostMapping("/orders/{orderId}/issues")
    @RequireAuth
    public ResponseEntity<OrderIssueResponse> openIssue(@PathVariable UUID orderId,
                                                        @Valid @RequestBody OpenIssueRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(issueService.openIssue(orderId, resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/orders/{orderId}/issues")
    @RequireAuth
    public ResponseEntity<List<OrderIssueResponse>> getIssuesByOrder(@PathVariable UUID orderId) {
        try {
            return ResponseEntity.ok(issueService.getIssuesByOrder(orderId, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/support/issues")
    @RequireAuth
    public ResponseEntity<PagedResponse<OrderIssueResponse>> listIssues(
            @RequestParam(required = false) OrderIssueState state,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(issueService.listIssues(resolveUserId(), state, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/support/issues/{id}/transition")
    @RequireAuth
    public ResponseEntity<OrderIssueResponse> transition(@PathVariable UUID id,
                                                         @Valid @RequestBody TransitionIssueRequest request) {
        try {
            return ResponseEntity.ok(issueService.transitionState(id, resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/support/issues/{id}/resolve/refund")
    @RequireAuth
    public ResponseEntity<OrderIssueResponse> resolveWithRefund(@PathVariable UUID id,
                                                                 @Valid @RequestBody ResolveWithRefundRequest request) {
        try {
            return ResponseEntity.ok(issueService.resolveWithRefund(id, resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/support/issues/{id}/resolve/replacement")
    @RequireAuth
    public ResponseEntity<OrderIssueResponse> resolveWithReplacement(@PathVariable UUID id,
                                                                      @Valid @RequestBody ResolveWithReplacementRequest request) {
        try {
            return ResponseEntity.ok(issueService.resolveWithReplacement(id, resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/support/issues/{id}/resolve/credit")
    @RequireAuth
    public ResponseEntity<OrderIssueResponse> resolveWithCredit(@PathVariable UUID id,
                                                                 @Valid @RequestBody ResolveWithCreditRequest request) {
        try {
            return ResponseEntity.ok(issueService.resolveWithCredit(id, resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/support/issues/{id}/reject")
    @RequireAuth
    public ResponseEntity<OrderIssueResponse> rejectIssue(@PathVariable UUID id,
                                                           @Valid @RequestBody RejectIssueRequest request) {
        try {
            return ResponseEntity.ok(issueService.rejectIssue(id, resolveUserId(), request));
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
