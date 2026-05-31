package backend.services.impl.support;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.support.AssignTicketRequest;
import backend.dtos.requests.support.CreateTicketRequest;
import backend.dtos.requests.support.TicketMessageRequest;
import backend.dtos.requests.support.UpdateTicketPriorityRequest;
import backend.dtos.requests.support.UpdateTicketStatusRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.support.TicketMessageResponse;
import backend.dtos.responses.support.TicketResponse;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Order;
import backend.models.core.SupportTicket;
import backend.models.core.SupportTicketMessage;
import backend.models.core.User;
import backend.models.enums.TicketMessageAuthor;
import backend.models.enums.TicketPriority;
import backend.models.enums.TicketStatus;
import backend.repositories.OrderRepository;
import backend.repositories.SupportTicketMessageRepository;
import backend.repositories.SupportTicketRepository;
import backend.repositories.UserRepository;
import backend.services.intf.support.SupportTicketService;
import backend.utilities.SecurityUtils;

import java.time.Instant;
import java.util.List;

@Service
public class SupportTicketServiceImpl implements SupportTicketService {

    private static final Logger log = LoggerFactory.getLogger(SupportTicketServiceImpl.class);

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public SupportTicketServiceImpl(SupportTicketRepository ticketRepository,
                                    SupportTicketMessageRepository messageRepository,
                                    UserRepository userRepository,
                                    OrderRepository orderRepository) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    public TicketResponse createTicket(UUID actorUserId, CreateTicketRequest request) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));

        User customer;
        if (SecurityUtils.isStaff(actor) && request.customerId() != null) {
            customer = userRepository.findById(request.customerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + request.customerId()));
        } else {
            customer = actor;
        }

        Order order = null;
        if (request.orderId() != null) {
            order = orderRepository.findById(request.orderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.orderId()));
            if (!SecurityUtils.isStaff(actor) && !order.getUser().getId().equals(actorUserId)) {
                throw new ForbiddenException();
            }
        }

        SupportTicket ticket = new SupportTicket();
        ticket.setCustomer(customer);
        ticket.setOpenedBy(actor);
        ticket.setOrder(order);
        ticket.setSubject(request.subject());
        ticket.setDescription(request.description());
        ticket.setCategory(request.category());
        ticket.setPriority(request.priority() != null ? request.priority() : TicketPriority.NORMAL);

        ticketRepository.save(ticket);

        appendSystemMessage(ticket, actor, "Ticket opened by " + formatName(actor));
        log.info("Support ticket {} created by user {}", ticket.getId(), actorUserId);
        return toResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TicketResponse> listTickets(UUID actorUserId, TicketStatus status,
                                                     UUID assignedToId, int page, int size) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<SupportTicket> tickets;
        if (SecurityUtils.isStaff(actor)) {
            tickets = ticketRepository.findAllByFilters(status, assignedToId, pageRequest);
        } else {
            tickets = ticketRepository.findAllByCustomerId(actorUserId, pageRequest);
        }

        return new PagedResponse<>(tickets.map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicket(UUID ticketId, UUID actorUserId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));

        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        if (!SecurityUtils.isStaff(actor) && !ticket.getCustomer().getId().equals(actorUserId)) {
            throw new ForbiddenException();
        }

        return toResponse(ticket);
    }

    @Override
    @Transactional
    public TicketMessageResponse addMessage(UUID ticketId, UUID authorUserId, TicketMessageRequest request) {
        User author = userRepository.findById(authorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + authorUserId));

        SupportTicket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        boolean isStaff = SecurityUtils.isStaff(author);
        if (!isStaff && !ticket.getCustomer().getId().equals(authorUserId)) {
            throw new ForbiddenException();
        }

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new backend.exceptions.http.ConflictException("Cannot add messages to a closed ticket");
        }

        TicketMessageAuthor role = isStaff ? TicketMessageAuthor.STAFF : TicketMessageAuthor.CUSTOMER;

        SupportTicketMessage msg = new SupportTicketMessage();
        msg.setTicket(ticket);
        msg.setAuthor(author);
        msg.setBody(request.body());
        msg.setAuthorRole(role);
        messageRepository.save(msg);

        // Auto-advance status — parentheses are required: && binds tighter than ||
        if (isStaff && (ticket.getStatus() == TicketStatus.OPEN || ticket.getStatus() == TicketStatus.PENDING_INTERNAL)) {
            ticket.setStatus(TicketStatus.PENDING_CUSTOMER);
        } else if (!isStaff && ticket.getStatus() == TicketStatus.PENDING_CUSTOMER) {
            ticket.setStatus(TicketStatus.PENDING_INTERNAL);
        }
        ticketRepository.save(ticket);

        return toMessageResponse(msg);
    }

    @Override
    @Transactional
    public TicketResponse assignTicket(UUID ticketId, UUID actorUserId, AssignTicketRequest request) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));
        SecurityUtils.requireStaff(actor);

        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        User assignee = userRepository.findById(request.staffUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + request.staffUserId()));
        SecurityUtils.requireStaff(assignee);

        ticket.setAssignedTo(assignee);
        ticketRepository.save(ticket);

        appendSystemMessage(ticket, actor, "Ticket assigned to " + formatName(assignee) + " by " + formatName(actor));
        return toResponse(ticket);
    }

    @Override
    @Transactional
    public TicketResponse updateStatus(UUID ticketId, UUID actorUserId, UpdateTicketStatusRequest request) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));
        SecurityUtils.requireStaff(actor);

        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        TicketStatus previous = ticket.getStatus();
        TicketStatus next = request.status();

        if (previous == TicketStatus.CLOSED) {
            throw new backend.exceptions.http.ConflictException("A closed ticket cannot be modified");
        }
        if (previous == next) {
            return toResponse(ticket); // no-op
        }

        ticket.setStatus(next);

        // Set or clear terminal timestamps to keep audit fields consistent with status.
        if (next == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(Instant.now());
            ticket.setClosedAt(null);
        } else if (next == TicketStatus.CLOSED) {
            ticket.setClosedAt(Instant.now());
        } else {
            // Reverting to a non-terminal status (e.g., OPEN, PENDING_*) — clear any
            // stale terminal timestamps so reports aren't misled.
            ticket.setResolvedAt(null);
            ticket.setClosedAt(null);
        }

        ticketRepository.save(ticket);
        appendSystemMessage(ticket, actor,
                "Status changed from " + previous + " to " + request.status() + " by " + formatName(actor));
        return toResponse(ticket);
    }

    @Override
    @Transactional
    public TicketResponse updatePriority(UUID ticketId, UUID actorUserId, UpdateTicketPriorityRequest request) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));
        SecurityUtils.requireStaff(actor);

        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        TicketPriority previous = ticket.getPriority();
        ticket.setPriority(request.priority());
        ticketRepository.save(ticket);

        appendSystemMessage(ticket, actor,
                "Priority changed from " + previous + " to " + request.priority() + " by " + formatName(actor));
        return toResponse(ticket);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void appendSystemMessage(SupportTicket ticket, User actor, String body) {
        SupportTicketMessage sys = new SupportTicketMessage();
        sys.setTicket(ticket);
        sys.setAuthor(actor);
        sys.setBody(body);
        sys.setAuthorRole(TicketMessageAuthor.SYSTEM);
        messageRepository.save(sys);
    }

    private TicketResponse toResponse(SupportTicket ticket) {
        List<TicketMessageResponse> messages = messageRepository
                .findAllByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream()
                .map(this::toMessageResponse)
                .toList();

        User customer = ticket.getCustomer();
        User assignedTo = ticket.getAssignedTo();

        return new TicketResponse(
                ticket.getId(),
                customer.getId(),
                formatName(customer),
                ticket.getOpenedBy().getId(),
                assignedTo != null ? assignedTo.getId() : null,
                assignedTo != null ? formatName(assignedTo) : null,
                ticket.getOrder() != null ? ticket.getOrder().getId() : null,
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getStatus().name(),
                ticket.getPriority().name(),
                ticket.getCategory().name(),
                messages,
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt(),
                ticket.getClosedAt()
        );
    }

    private TicketMessageResponse toMessageResponse(SupportTicketMessage msg) {
        User author = msg.getAuthor();
        return new TicketMessageResponse(
                msg.getId(),
                author.getId(),
                formatName(author),
                msg.getAuthorRole().name(),
                msg.getBody(),
                msg.getCreatedAt()
        );
    }

    private String formatName(User user) {
        if (user.getFirstName() != null && user.getLastName() != null) {
            return user.getFirstName() + " " + user.getLastName();
        }
        return user.getEmail();
    }
}
