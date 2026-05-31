package backend.services.impl.orders;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.issue.OpenIssueRequest;
import backend.dtos.requests.issue.RejectIssueRequest;
import backend.dtos.requests.issue.ResolveWithCreditRequest;
import backend.dtos.requests.issue.ResolveWithRefundRequest;
import backend.dtos.requests.issue.ResolveWithReplacementRequest;
import backend.dtos.requests.issue.TransitionIssueRequest;
import backend.dtos.requests.credit.IssueCreditRequest;
import backend.dtos.requests.support.CreateTicketRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.issue.OrderIssueResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Order;
import backend.models.core.OrderIssue;
import backend.models.core.SupportTicket;
import backend.models.core.User;
import backend.models.enums.CreditEntryType;
import backend.models.enums.IssueResolution;
import backend.models.enums.OrderIssueState;
import backend.models.enums.TicketCategory;
import backend.repositories.OrderIssueRepository;
import backend.repositories.OrderRepository;
import backend.repositories.SupportTicketRepository;
import backend.repositories.UserRepository;
import backend.dtos.responses.return_.ReturnResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.services.intf.customers.CustomerCreditService;
import backend.services.intf.orders.OrderIssueService;
import backend.services.intf.orders.ReplacementOrderService;
import backend.services.intf.returns.ReturnService;
import backend.services.intf.support.SupportTicketService;
import backend.utilities.SecurityUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class OrderIssueServiceImpl implements OrderIssueService {

    private static final Logger log = LoggerFactory.getLogger(OrderIssueServiceImpl.class);

    private static final Set<OrderIssueState> TERMINAL_STATES = Set.of(
            OrderIssueState.RESOLVED_REFUND,
            OrderIssueState.RESOLVED_REPLACEMENT,
            OrderIssueState.RESOLVED_CREDIT,
            OrderIssueState.REJECTED,
            OrderIssueState.CANCELLED
    );

    private final OrderIssueRepository issueRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final SupportTicketRepository ticketRepository;
    private final ReturnService returnService;
    private final ReplacementOrderService replacementOrderService;
    private final CustomerCreditService customerCreditService;
    private final SupportTicketService supportTicketService;

    public OrderIssueServiceImpl(OrderIssueRepository issueRepository,
                                  OrderRepository orderRepository,
                                  UserRepository userRepository,
                                  SupportTicketRepository ticketRepository,
                                  ReturnService returnService,
                                  ReplacementOrderService replacementOrderService,
                                  CustomerCreditService customerCreditService,
                                  SupportTicketService supportTicketService) {
        this.issueRepository = issueRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.returnService = returnService;
        this.replacementOrderService = replacementOrderService;
        this.customerCreditService = customerCreditService;
        this.supportTicketService = supportTicketService;
    }

    @Override
    @Transactional
    public OrderIssueResponse openIssue(UUID orderId, UUID reporterUserId, OpenIssueRequest request) {
        User reporter = userRepository.findById(reporterUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + reporterUserId));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!SecurityUtils.isStaff(reporter) && !order.getUser().getId().equals(reporterUserId)) {
            throw new ForbiddenException();
        }

        OrderIssue issue = new OrderIssue();
        issue.setOrder(order);
        issue.setReportedBy(reporter);
        issue.setType(request.type());
        issue.setDescription(request.description());
        issue.setState(OrderIssueState.REPORTED);

        SupportTicket ticket = null;
        if (request.openTicket()) {
            CreateTicketRequest ticketRequest = new CreateTicketRequest(
                    request.type().name().replace("_", " ").toLowerCase(),
                    request.description() != null ? request.description() : "Issue reported: " + request.type(),
                    TicketCategory.ORDER_ISSUE,
                    null,
                    orderId,
                    SecurityUtils.isStaff(reporter) ? order.getUser().getId() : null
            );
            var ticketResponse = supportTicketService.createTicket(reporterUserId, ticketRequest);
            ticket = ticketRepository.findById(ticketResponse.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket not found after creation"));
            issue.setTicket(ticket);
        }

        issueRepository.save(issue);
        log.info("Order issue {} opened for order {} by user {}", issue.getId(), orderId, reporterUserId);
        return toResponse(issue);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderIssueResponse> getIssuesByOrder(UUID orderId, UUID actorUserId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!SecurityUtils.isStaff(actor) && !order.getUser().getId().equals(actorUserId)) {
            throw new ForbiddenException();
        }

        return issueRepository.findAllByOrderId(orderId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderIssueResponse> listIssues(UUID actorUserId, OrderIssueState state, int page, int size) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));
        SecurityUtils.requireStaff(actor);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderIssue> issues = issueRepository.findAllByFilters(state, pageRequest);
        return new PagedResponse<>(issues.map(this::toResponse));
    }

    @Override
    @Transactional
    public OrderIssueResponse transitionState(UUID issueId, UUID actorUserId, TransitionIssueRequest request) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));
        SecurityUtils.requireStaff(actor);

        OrderIssue issue = requireIssue(issueId);
        requireNonTerminal(issue);

        issue.setState(request.state());
        issueRepository.save(issue);
        return toResponse(issue);
    }

    @Override
    @Transactional
    public OrderIssueResponse resolveWithRefund(UUID issueId, UUID actorUserId, ResolveWithRefundRequest request) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));
        SecurityUtils.requireStaff(actor);

        OrderIssue issue = requireIssue(issueId);
        requireNonTerminal(issue);

        ReturnResponse returnResp = returnService.issuePartialRefund(
                issue.getOrder().getId(),
                request.refundAmountCents(),
                request.reason(),
                actorUserId);

        // returnId is a loose FK still typed Long in entity — not stored until entity migrates
        // issue.setReturnId(returnResp.id());
        issue.setState(OrderIssueState.RESOLVED_REFUND);
        issue.setResolution(IssueResolution.REFUND);
        issue.setResolvedAt(Instant.now());
        issueRepository.save(issue);

        log.info("Issue {} resolved with refund (return {})", issueId, returnResp.id());
        return toResponse(issue);
    }

    @Override
    @Transactional
    public OrderIssueResponse resolveWithReplacement(UUID issueId, UUID actorUserId,
                                                      ResolveWithReplacementRequest request) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));
        SecurityUtils.requireStaff(actor);

        OrderIssue issue = requireIssue(issueId);
        requireNonTerminal(issue);

        OrderResponse replacementOrder = replacementOrderService.createReplacement(
                issue.getOrder().getId(), request, actorUserId);

        // replacementOrderId is a loose FK still typed Long in entity — not stored until entity migrates
        // issue.setReplacementOrderId(replacementOrder.getId());
        issue.setState(OrderIssueState.RESOLVED_REPLACEMENT);
        issue.setResolution(IssueResolution.REPLACEMENT);
        issue.setResolvedAt(Instant.now());
        issueRepository.save(issue);

        log.info("Issue {} resolved with replacement order {}", issueId, replacementOrder.getId());
        return toResponse(issue);
    }

    @Override
    @Transactional
    public OrderIssueResponse resolveWithCredit(UUID issueId, UUID actorUserId, ResolveWithCreditRequest request) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));
        SecurityUtils.requireStaff(actor);

        OrderIssue issue = requireIssue(issueId);
        requireNonTerminal(issue);

        UUID customerId = issue.getOrder().getUser().getId();
        // Deduct any credit the customer already spent on this order so we don't
        // return more than they actually paid. E.g. $100 order paid $30 credit +
        // $70 Stripe → compensation is capped at $100 - $30 = $70 new credit.
        long creditAlreadyApplied = issue.getOrder().getCreditAppliedCents();
        long netCompensation = Math.max(0L, request.amountCents() - creditAlreadyApplied);
        IssueCreditRequest creditRequest = new IssueCreditRequest(
                netCompensation,
                CreditEntryType.COMPENSATION_ISSUED,
                request.reason(),
                null
        );
        var creditEntry = customerCreditService.issueCredit(
                customerId, creditRequest, actorUserId,
                issue.getTicket() != null ? issue.getTicket().getId() : null,
                issueId);

        issue.setCustomerCreditId(creditEntry.getId());
        issue.setState(OrderIssueState.RESOLVED_CREDIT);
        issue.setResolution(IssueResolution.CREDIT);
        issue.setResolvedAt(Instant.now());
        issueRepository.save(issue);

        log.info("Issue {} resolved with credit entry {}", issueId, creditEntry.getId());
        return toResponse(issue);
    }

    @Override
    @Transactional
    public OrderIssueResponse rejectIssue(UUID issueId, UUID actorUserId, RejectIssueRequest request) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));
        SecurityUtils.requireStaff(actor);

        OrderIssue issue = requireIssue(issueId);
        requireNonTerminal(issue);

        issue.setState(OrderIssueState.REJECTED);
        issue.setResolution(IssueResolution.REJECTED);
        issue.setRejectionReason(request.reason());
        issue.setResolvedAt(Instant.now());
        issueRepository.save(issue);

        log.info("Issue {} rejected by staff {}", issueId, actorUserId);
        return toResponse(issue);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private OrderIssue requireIssue(UUID issueId) {
        return issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Order issue not found: " + issueId));
    }

    private void requireNonTerminal(OrderIssue issue) {
        if (TERMINAL_STATES.contains(issue.getState())) {
            throw new BadRequestException("Issue " + issue.getId() + " is already in a terminal state: " + issue.getState());
        }
    }

    private OrderIssueResponse toResponse(OrderIssue issue) {
        return new OrderIssueResponse(
                issue.getId(),
                issue.getOrder().getId(),
                issue.getTicket() != null ? issue.getTicket().getId() : null,
                issue.getReportedBy().getId(),
                issue.getType().name(),
                issue.getState().name(),
                issue.getResolution() != null ? issue.getResolution().name() : null,
                issue.getDescription(),
                issue.getRejectionReason(),
                null, // returnId is a loose FK still typed Long in entity
                null, // replacementOrderId is a loose FK still typed Long in entity
                issue.getCustomerCreditId(),
                issue.getCreatedAt(),
                issue.getUpdatedAt(),
                issue.getResolvedAt()
        );
    }
}
