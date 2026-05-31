package backend.services.intf.orders;

import java.util.UUID;
import backend.dtos.requests.issue.OpenIssueRequest;
import backend.dtos.requests.issue.RejectIssueRequest;
import backend.dtos.requests.issue.ResolveWithCreditRequest;
import backend.dtos.requests.issue.ResolveWithRefundRequest;
import backend.dtos.requests.issue.ResolveWithReplacementRequest;
import backend.dtos.requests.issue.TransitionIssueRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.issue.OrderIssueResponse;
import backend.models.enums.OrderIssueState;

import java.util.List;

public interface OrderIssueService {

    /** Opens a new issue for an order. Both customers and staff can report. */
    OrderIssueResponse openIssue(UUID orderId, UUID reporterUserId, OpenIssueRequest request);

    /** Lists all issues for a specific order (customer scoped to own orders; staff unrestricted). */
    List<OrderIssueResponse> getIssuesByOrder(UUID orderId, UUID actorUserId);

    /** Staff triage list — filter by state. */
    PagedResponse<OrderIssueResponse> listIssues(UUID actorUserId, OrderIssueState state, int page, int size);

    /** Staff-only: advance the issue through the state machine. */
    OrderIssueResponse transitionState(UUID issueId, UUID actorUserId, TransitionIssueRequest request);

    /** Staff-only: resolve via Stripe refund (delegates to ReturnService). */
    OrderIssueResponse resolveWithRefund(UUID issueId, UUID actorUserId, ResolveWithRefundRequest request);

    /** Staff-only: resolve by creating a replacement order. */
    OrderIssueResponse resolveWithReplacement(UUID issueId, UUID actorUserId, ResolveWithReplacementRequest request);

    /** Staff-only: resolve by issuing store credit. */
    OrderIssueResponse resolveWithCredit(UUID issueId, UUID actorUserId, ResolveWithCreditRequest request);

    /** Staff-only: reject the issue with a reason. */
    OrderIssueResponse rejectIssue(UUID issueId, UUID actorUserId, RejectIssueRequest request);
}
