package backend.services.intf.support;

import java.util.UUID;
import backend.dtos.requests.support.AssignTicketRequest;
import backend.dtos.requests.support.CreateTicketRequest;
import backend.dtos.requests.support.TicketMessageRequest;
import backend.dtos.requests.support.UpdateTicketPriorityRequest;
import backend.dtos.requests.support.UpdateTicketStatusRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.support.TicketMessageResponse;
import backend.dtos.responses.support.TicketResponse;
import backend.models.enums.TicketStatus;

public interface SupportTicketService {

    /**
     * Creates a new support ticket. If the actor is staff and {@code request.customerId} is set,
     * the ticket is opened on behalf of that customer. Otherwise the actor is the customer.
     */
    TicketResponse createTicket(UUID actorUserId, CreateTicketRequest request);

    /**
     * Lists tickets. Staff see all; customers see only their own.
     * Staff can filter by status and assignedToId.
     */
    PagedResponse<TicketResponse> listTickets(UUID actorUserId, TicketStatus status,
                                              UUID assignedToId, int page, int size);

    /** Returns a single ticket. Customers can only view their own. */
    TicketResponse getTicket(UUID ticketId, UUID actorUserId);

    /**
     * Appends a message to the ticket thread.
     * Auto-advances status: staff reply → PENDING_CUSTOMER; customer reply → PENDING_INTERNAL.
     */
    TicketMessageResponse addMessage(UUID ticketId, UUID authorUserId, TicketMessageRequest request);

    /** Staff-only: assign ticket to a support user. */
    TicketResponse assignTicket(UUID ticketId, UUID actorUserId, AssignTicketRequest request);

    /** Staff-only: change ticket status. */
    TicketResponse updateStatus(UUID ticketId, UUID actorUserId, UpdateTicketStatusRequest request);

    /** Staff-only: change ticket priority. */
    TicketResponse updatePriority(UUID ticketId, UUID actorUserId, UpdateTicketPriorityRequest request);
}
