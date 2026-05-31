package backend.services.intf.orders;

import java.util.UUID;
import backend.dtos.requests.issue.ResolveWithReplacementRequest;
import backend.dtos.responses.order.OrderResponse;

public interface ReplacementOrderService {

    /**
     * Creates a zero-cost replacement order for the given original order.
     * The new order starts in PAID status ready for fulfillment and has
     * {@code replacementOfOrderId} set to the original order's ID.
     *
     * @param originalOrderId the order being replaced
     * @param request         items and shipping address for the replacement
     * @param actorUserId     staff member authorising the replacement
     * @return the newly created replacement order
     */
    OrderResponse createReplacement(UUID originalOrderId, ResolveWithReplacementRequest request, UUID actorUserId);
}
