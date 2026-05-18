package backend.controllers.impl.support;

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
import backend.dtos.requests.support.AssignTicketRequest;
import backend.dtos.requests.support.CreateTicketRequest;
import backend.dtos.requests.support.TicketMessageRequest;
import backend.dtos.requests.support.UpdateTicketPriorityRequest;
import backend.dtos.requests.support.UpdateTicketStatusRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.support.TicketMessageResponse;
import backend.dtos.responses.support.TicketResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.models.enums.TicketStatus;
import backend.services.intf.RateLimitService;
import backend.services.intf.support.SupportTicketService;

@RestController
@RequestMapping("/support/tickets")
public class SupportTicketController {

    private static final int TICKET_CREATE_LIMIT = 5;
    private static final int TICKET_MESSAGE_LIMIT = 20;
    private static final int TICKET_WINDOW_SECONDS = 3600;

    private final SupportTicketService ticketService;
    private final RateLimitService rateLimitService;

    public SupportTicketController(SupportTicketService ticketService, RateLimitService rateLimitService) {
        this.ticketService = ticketService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    @RequireAuth
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        try {
            UUID userId = resolveUserId();
            rateLimitService.enforce("ticket:create", userId.toString(), TICKET_CREATE_LIMIT, TICKET_WINDOW_SECONDS);
            return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.createTicket(userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping
    @RequireAuth
    public ResponseEntity<PagedResponse<TicketResponse>> listTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) UUID assignedToId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(ticketService.listTickets(resolveUserId(), status, assignedToId, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{id}")
    @RequireAuth
    public ResponseEntity<TicketResponse> getTicket(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(ticketService.getTicket(id, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{id}/messages")
    @RequireAuth
    public ResponseEntity<TicketMessageResponse> addMessage(@PathVariable UUID id,
                                                            @Valid @RequestBody TicketMessageRequest request) {
        try {
            UUID userId = resolveUserId();
            rateLimitService.enforce("ticket:message", userId.toString(), TICKET_MESSAGE_LIMIT, TICKET_WINDOW_SECONDS);
            return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.addMessage(id, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{id}/status")
    @RequireAuth
    public ResponseEntity<TicketResponse> updateStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateTicketStatusRequest request) {
        try {
            return ResponseEntity.ok(ticketService.updateStatus(id, resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{id}/assign")
    @RequireAuth
    public ResponseEntity<TicketResponse> assignTicket(@PathVariable UUID id,
                                                       @Valid @RequestBody AssignTicketRequest request) {
        try {
            return ResponseEntity.ok(ticketService.assignTicket(id, resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{id}/priority")
    @RequireAuth
    public ResponseEntity<TicketResponse> updatePriority(@PathVariable UUID id,
                                                         @Valid @RequestBody UpdateTicketPriorityRequest request) {
        try {
            return ResponseEntity.ok(ticketService.updatePriority(id, resolveUserId(), request));
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
