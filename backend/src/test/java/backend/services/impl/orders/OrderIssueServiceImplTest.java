package backend.services.impl.orders;

import backend.dtos.requests.issue.OpenIssueRequest;
import backend.dtos.requests.issue.RejectIssueRequest;
import backend.dtos.requests.issue.ResolveWithCreditRequest;
import backend.dtos.requests.issue.ResolveWithRefundRequest;
import backend.dtos.requests.issue.TransitionIssueRequest;
import backend.dtos.responses.credit.CreditEntryResponse;
import backend.dtos.responses.issue.OrderIssueResponse;
import backend.dtos.responses.return_.ReturnResponse;
import backend.dtos.responses.support.TicketResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.models.core.Order;
import backend.models.core.OrderIssue;
import backend.models.core.SupportTicket;
import backend.models.core.User;
import backend.models.enums.CreditEntryType;
import backend.models.enums.IssueResolution;
import backend.models.enums.OrderIssueState;
import backend.models.enums.OrderIssueType;
import backend.models.enums.UserRole;
import backend.repositories.OrderIssueRepository;
import backend.repositories.OrderRepository;
import backend.repositories.SupportTicketRepository;
import backend.repositories.UserRepository;
import backend.services.intf.customers.CustomerCreditService;
import backend.services.intf.orders.ReplacementOrderService;
import backend.services.intf.returns.ReturnService;
import backend.services.intf.support.SupportTicketService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderIssueServiceImplTest {

    private OrderIssueRepository issueRepository;
    private OrderRepository orderRepository;
    private UserRepository userRepository;
    private SupportTicketRepository ticketRepository;
    private ReturnService returnService;
    private ReplacementOrderService replacementOrderService;
    private CustomerCreditService customerCreditService;
    private SupportTicketService supportTicketService;
    private OrderIssueServiceImpl service;

    @BeforeEach
    void setUp() {
        issueRepository          = mock(OrderIssueRepository.class);
        orderRepository          = mock(OrderRepository.class);
        userRepository           = mock(UserRepository.class);
        ticketRepository         = mock(SupportTicketRepository.class);
        returnService            = mock(ReturnService.class);
        replacementOrderService  = mock(ReplacementOrderService.class);
        customerCreditService    = mock(CustomerCreditService.class);
        supportTicketService     = mock(SupportTicketService.class);
        service = new OrderIssueServiceImpl(issueRepository, orderRepository, userRepository,
                ticketRepository, returnService, replacementOrderService,
                customerCreditService, supportTicketService);
    }

    // ─── openIssue ───────────────────────────────────────────────────────────

    @Test
    void openIssue_customerCanOpenForOwnOrder() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        Order order   = makeOrder(TestIds.uuid(10), customer);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(orderRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(order));
        when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OpenIssueRequest req = new OpenIssueRequest(OrderIssueType.DAMAGED, "Box was crushed", false);
        OrderIssueResponse resp = service.openIssue(TestIds.uuid(10), TestIds.uuid(1), req);

        assertEquals(OrderIssueState.REPORTED.name(), resp.getState());
        assertEquals(OrderIssueType.DAMAGED.name(), resp.getType());
    }

    @Test
    void openIssue_customerCannotOpenForOtherUsersOrder() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        User other    = makeUser(TestIds.uuid(2), UserRole.USER);
        Order order   = makeOrder(TestIds.uuid(10), other);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(orderRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(order));

        assertThrows(ForbiddenException.class,
                () -> service.openIssue(TestIds.uuid(10), TestIds.uuid(1),
                        new OpenIssueRequest(OrderIssueType.DAMAGED, null, false)));
    }

    // ─── transitionState ─────────────────────────────────────────────────────

    @Test
    void transitionState_staffCanAdvanceState() {
        User staff   = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        OrderIssue issue = makeIssue(TestIds.uuid(5), makeOrder(TestIds.uuid(10), makeUser(TestIds.uuid(1), UserRole.USER)), staff);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(issueRepository.findById(TestIds.uuid(5))).thenReturn(Optional.of(issue));
        when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transitionState(TestIds.uuid(5), TestIds.uuid(2), new TransitionIssueRequest(OrderIssueState.INVESTIGATING));
        assertEquals(OrderIssueState.INVESTIGATING, issue.getState());
    }

    @Test
    void transitionState_customerForbidden() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));

        assertThrows(ForbiddenException.class,
                () -> service.transitionState(TestIds.uuid(5), TestIds.uuid(1),
                        new TransitionIssueRequest(OrderIssueState.INVESTIGATING)));
    }

    // ─── resolveWithRefund ───────────────────────────────────────────────────

    @Test
    void resolveWithRefund_setsTerminalState() {
        User staff    = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        OrderIssue issue = makeIssue(TestIds.uuid(5), makeOrder(TestIds.uuid(10), customer), staff);

        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(issueRepository.findById(TestIds.uuid(5))).thenReturn(Optional.of(issue));
        when(returnService.issuePartialRefund(any(UUID.class), anyLong(), any(), any(UUID.class)))
                .thenReturn(makeReturnResponse(TestIds.uuid(20)));
        when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.resolveWithRefund(TestIds.uuid(5), TestIds.uuid(2), new ResolveWithRefundRequest(500L, "Compensation"));

        assertEquals(OrderIssueState.RESOLVED_REFUND, issue.getState());
        assertNotNull(issue.getResolvedAt());
    }

    // ─── resolveWithCredit ───────────────────────────────────────────────────

    @Test
    void resolveWithCredit_setsCreditIdAndTerminalState() {
        User staff    = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        OrderIssue issue = makeIssue(TestIds.uuid(5), makeOrder(TestIds.uuid(10), customer), staff);

        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(issueRepository.findById(TestIds.uuid(5))).thenReturn(Optional.of(issue));
        when(customerCreditService.issueCredit(any(UUID.class), any(), any(UUID.class), any(), any()))
                .thenReturn(makeCreditResponse(TestIds.uuid(30)));
        when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.resolveWithCredit(TestIds.uuid(5), TestIds.uuid(2), new ResolveWithCreditRequest(300L, "Sorry"));

        assertEquals(OrderIssueState.RESOLVED_CREDIT, issue.getState());
        assertEquals(TestIds.uuid(30), issue.getCustomerCreditId());
    }

    // ─── rejectIssue ─────────────────────────────────────────────────────────

    @Test
    void rejectIssue_setsRejectedStateAndReason() {
        User staff    = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        OrderIssue issue = makeIssue(TestIds.uuid(5), makeOrder(TestIds.uuid(10), customer), staff);

        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(issueRepository.findById(TestIds.uuid(5))).thenReturn(Optional.of(issue));
        when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.rejectIssue(TestIds.uuid(5), TestIds.uuid(2), new RejectIssueRequest("No evidence provided"));

        assertEquals(OrderIssueState.REJECTED, issue.getState());
        assertEquals("No evidence provided", issue.getRejectionReason());
    }

    @Test
    void resolveWithRefund_throwsWhenIssueAlreadyTerminal() {
        User staff    = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        OrderIssue issue = makeIssue(TestIds.uuid(5), makeOrder(TestIds.uuid(10), customer), staff);
        issue.setState(OrderIssueState.RESOLVED_REFUND);

        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(issueRepository.findById(TestIds.uuid(5))).thenReturn(Optional.of(issue));

        assertThrows(BadRequestException.class,
                () -> service.resolveWithRefund(TestIds.uuid(5), TestIds.uuid(2),
                        new ResolveWithRefundRequest(100L, null)));
    }

    @Test
    void openIssue_withOpenTicket_createsTicketAndLinksToIssue() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        Order order   = makeOrder(TestIds.uuid(10), customer);
        UUID ticketId = TestIds.uuid(50);
        SupportTicket ticket = makeTicket(ticketId);

        TicketResponse ticketResp = new TicketResponse(ticketId, TestIds.uuid(1), "User", TestIds.uuid(1),
                null, null, TestIds.uuid(10), "damaged item", "desc", "OPEN", "MEDIUM", "ORDER_ISSUE",
                List.of(), Instant.now(), Instant.now(), null, null);

        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(orderRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(order));
        when(supportTicketService.createTicket(eq(TestIds.uuid(1)), any())).thenReturn(ticketResp);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OpenIssueRequest req = new OpenIssueRequest(OrderIssueType.NOT_RECEIVED, "Item missing", true);
        OrderIssueResponse resp = service.openIssue(TestIds.uuid(10), TestIds.uuid(1), req);

        assertEquals(OrderIssueState.REPORTED.name(), resp.getState());
        verify(supportTicketService).createTicket(eq(TestIds.uuid(1)), any());
        verify(ticketRepository).findById(ticketId);
    }

    @Test
    void openIssue_staffOpensForAnotherUsersOrder_allowed() {
        User staff    = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        Order order   = makeOrder(TestIds.uuid(10), customer);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(orderRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(order));
        when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OpenIssueRequest req = new OpenIssueRequest(OrderIssueType.WRONG_ITEM, null, false);
        OrderIssueResponse resp = service.openIssue(TestIds.uuid(10), TestIds.uuid(2), req);

        assertEquals(OrderIssueState.REPORTED.name(), resp.getState());
    }

    @Test
    void getIssuesByOrder_customerViewsOtherUserOrder_throwsForbidden() {
        User viewer = makeUser(TestIds.uuid(3), UserRole.USER);
        User owner  = makeUser(TestIds.uuid(4), UserRole.USER);
        Order order = makeOrder(TestIds.uuid(10), owner);
        when(userRepository.findById(TestIds.uuid(3))).thenReturn(Optional.of(viewer));
        when(orderRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(order));

        assertThrows(ForbiddenException.class,
                () -> service.getIssuesByOrder(TestIds.uuid(10), TestIds.uuid(3)));
    }

    @Test
    void getIssuesByOrder_staffViewsAnyOrder_allowed() {
        User staff = makeUser(TestIds.uuid(5), UserRole.SUPPORT);
        User owner = makeUser(TestIds.uuid(6), UserRole.USER);
        Order order = makeOrder(TestIds.uuid(11), owner);
        when(userRepository.findById(TestIds.uuid(5))).thenReturn(Optional.of(staff));
        when(orderRepository.findById(TestIds.uuid(11))).thenReturn(Optional.of(order));
        when(issueRepository.findAllByOrderId(TestIds.uuid(11))).thenReturn(List.of());

        List<OrderIssueResponse> result = service.getIssuesByOrder(TestIds.uuid(11), TestIds.uuid(5));
        assertNotNull(result);
    }

    @Test
    void transitionState_throwsWhenIssueIsTerminal() {
        User staff = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        OrderIssue issue = makeIssue(TestIds.uuid(5), makeOrder(TestIds.uuid(10), makeUser(TestIds.uuid(1), UserRole.USER)), staff);
        issue.setState(OrderIssueState.RESOLVED_CREDIT);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(issueRepository.findById(TestIds.uuid(5))).thenReturn(Optional.of(issue));

        assertThrows(BadRequestException.class,
                () -> service.transitionState(TestIds.uuid(5), TestIds.uuid(2),
                        new TransitionIssueRequest(OrderIssueState.INVESTIGATING)));
    }

    @Test
    void resolveWithCredit_withExistingTicket_passesTicketIdToCreditService() {
        User staff    = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        Order order   = makeOrder(TestIds.uuid(10), customer);
        UUID ticketId = TestIds.uuid(60);
        SupportTicket ticket = makeTicket(ticketId);
        OrderIssue issue = makeIssue(TestIds.uuid(5), order, staff);
        issue.setTicket(ticket);

        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(issueRepository.findById(TestIds.uuid(5))).thenReturn(Optional.of(issue));
        when(customerCreditService.issueCredit(any(UUID.class), any(), any(UUID.class), eq(ticketId), any()))
                .thenReturn(makeCreditResponse(TestIds.uuid(70)));
        when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.resolveWithCredit(TestIds.uuid(5), TestIds.uuid(2), new ResolveWithCreditRequest(200L, "Apology"));

        verify(customerCreditService).issueCredit(any(), any(), any(), eq(ticketId), any());
        assertEquals(OrderIssueState.RESOLVED_CREDIT, issue.getState());
        assertEquals(IssueResolution.CREDIT, issue.getResolution());
    }

    @Test
    void rejectIssue_throwsWhenIssueIsTerminal() {
        User staff    = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        OrderIssue issue = makeIssue(TestIds.uuid(5), makeOrder(TestIds.uuid(10), customer), staff);
        issue.setState(OrderIssueState.REJECTED);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(issueRepository.findById(TestIds.uuid(5))).thenReturn(Optional.of(issue));

        assertThrows(BadRequestException.class,
                () -> service.rejectIssue(TestIds.uuid(5), TestIds.uuid(2),
                        new RejectIssueRequest("Already rejected")));
    }

    @Test
    void resolveWithReplacement_throwsWhenTerminal() {
        User staff    = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        OrderIssue issue = makeIssue(TestIds.uuid(5), makeOrder(TestIds.uuid(10), customer), staff);
        issue.setState(OrderIssueState.RESOLVED_REPLACEMENT);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(issueRepository.findById(TestIds.uuid(5))).thenReturn(Optional.of(issue));

        var req = new backend.dtos.requests.issue.ResolveWithReplacementRequest(
                List.of(), "123 Main St", "Toronto", "CA", "M5V1A1");

        assertThrows(BadRequestException.class,
                () -> service.resolveWithReplacement(TestIds.uuid(5), TestIds.uuid(2), req));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User makeUser(UUID id, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@test.com");
        u.setRole(role);
        return u;
    }

    private Order makeOrder(UUID id, User owner) {
        Order o = new Order();
        o.setId(id);
        o.setUser(owner);
        return o;
    }

    private OrderIssue makeIssue(UUID id, Order order, User reporter) {
        OrderIssue i = new OrderIssue();
        i.setId(id);
        i.setOrder(order);
        i.setReportedBy(reporter);
        i.setType(OrderIssueType.DAMAGED);
        i.setState(OrderIssueState.REPORTED);
        return i;
    }

    private ReturnResponse makeReturnResponse(UUID id) {
        return new ReturnResponse(id, TestIds.uuid(10), null, "COMPLETED", null, null, null,
                false, List.of(), List.of(), null, null, null, null,
                500L, "PENDING", Instant.now(), Instant.now(), Instant.now(), Instant.now());
    }

    private CreditEntryResponse makeCreditResponse(UUID id) {
        return new CreditEntryResponse(id, 300L, "USD", CreditEntryType.COMPENSATION_ISSUED.name(),
                "Sorry", null, null, null, null, null, Instant.now());
    }

    private SupportTicket makeTicket(UUID id) {
        SupportTicket t = new SupportTicket();
        t.setId(id);
        return t;
    }

    // ─── Additional tests for uncovered methods ───────────────────────────────

    @Test
    void getIssuesByOrder_customerViewsOwnOrder_returnsIssues() {
        UUID orderId = TestIds.uuid(91); UUID userId = TestIds.uuid(92);
        User user = makeUser(userId, UserRole.USER);
        Order order = makeOrder(orderId, user);
        // Service calls userRepository.findById then orderRepository.findById
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(issueRepository.findAllByOrderId(orderId)).thenReturn(List.of());

        List<OrderIssueResponse> result = service.getIssuesByOrder(orderId, userId);

        assertNotNull(result);
        verify(issueRepository).findAllByOrderId(orderId);
    }

    @Test
    void listIssues_noStateFilter_returnsPagedResults() {
        UUID staffId = TestIds.uuid(93);
        User staff = makeUser(staffId, UserRole.SUPPORT);
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(issueRepository.findAllByFilters(isNull(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        var result = service.listIssues(staffId, null, 0, 20);

        assertNotNull(result);
    }

    @Test
    void listIssues_withStateFilter_queriesByFilters() {
        UUID staffId = TestIds.uuid(94);
        User staff = makeUser(staffId, UserRole.SUPPORT);
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(issueRepository.findAllByFilters(eq(OrderIssueState.REPORTED), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        var result = service.listIssues(staffId, OrderIssueState.REPORTED, 0, 20);

        assertNotNull(result);
        verify(issueRepository).findAllByFilters(eq(OrderIssueState.REPORTED), any());
    }

    @Test
    void listIssues_nonStaffUser_throwsForbidden() {
        UUID userId = TestIds.uuid(80);
        User customer = makeUser(userId, UserRole.USER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(customer));

        assertThrows(ForbiddenException.class,
                () -> service.listIssues(userId, null, 0, 20));
    }

    @Test
    void resolveWithCredit_creditAppliedExceedsRequest_netCompensationIsZero() {
        UUID issueId = TestIds.uuid(81); UUID staffId = TestIds.uuid(82);
        User staff    = makeUser(staffId, UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(83), UserRole.USER);
        Order order   = makeOrder(TestIds.uuid(84), customer);
        order.setCreditAppliedCents(500L); // already applied 500c
        OrderIssue issue = makeIssue(issueId, order, staff);

        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(customerCreditService.issueCredit(any(), any(), any(), any(), any()))
                .thenReturn(makeCreditResponse(TestIds.uuid(85)));
        when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // request.amountCents (200) < creditAlreadyApplied (500) → netCompensation clamped to 0
        service.resolveWithCredit(issueId, staffId, new ResolveWithCreditRequest(200L, "Partial refund"));

        verify(customerCreditService).issueCredit(any(),
                argThat(req -> req instanceof backend.dtos.requests.credit.IssueCreditRequest cr
                        && cr.amountCents() == 0L),
                any(), any(), any());
    }

    @Test
    void openIssue_staffOpenerWithOpenTicket_passesCustomerIdToTicket() {
        User staff    = makeUser(TestIds.uuid(86), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(87), UserRole.USER);
        Order order   = makeOrder(TestIds.uuid(88), customer);
        UUID ticketId = TestIds.uuid(89);
        SupportTicket ticket = makeTicket(ticketId);

        backend.dtos.responses.support.TicketResponse ticketResp =
                new backend.dtos.responses.support.TicketResponse(
                        ticketId, TestIds.uuid(86), "Staff", TestIds.uuid(86),
                        null, null, TestIds.uuid(88), "wrong item", "desc",
                        "OPEN", "MEDIUM", "ORDER_ISSUE",
                        List.of(), Instant.now(), Instant.now(), null, null);

        when(userRepository.findById(TestIds.uuid(86))).thenReturn(Optional.of(staff));
        when(orderRepository.findById(TestIds.uuid(88))).thenReturn(Optional.of(order));
        when(supportTicketService.createTicket(eq(TestIds.uuid(86)), any())).thenReturn(ticketResp);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // openTicket=true, staff opener → line 118 passes customer.getId() into ticket request
        OpenIssueRequest req = new OpenIssueRequest(OrderIssueType.WRONG_ITEM, "Wrong color", true);
        OrderIssueResponse resp = service.openIssue(TestIds.uuid(88), TestIds.uuid(86), req);

        assertEquals(OrderIssueState.REPORTED.name(), resp.getState());
        verify(supportTicketService).createTicket(eq(TestIds.uuid(86)), any());
    }

    @Test
    void resolveWithReplacement_setsTerminalState() {
        UUID issueId = TestIds.uuid(95); UUID actorId = TestIds.uuid(96);
        User staff = makeUser(actorId, UserRole.SUPPORT);
        Order order = makeOrder(TestIds.uuid(97), staff);
        OrderIssue issue = makeIssue(issueId, order, staff);
        issue.setState(OrderIssueState.INVESTIGATING);

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(staff));

        var req = new backend.dtos.requests.issue.ResolveWithReplacementRequest(
                List.of(new backend.dtos.requests.issue.ResolveWithReplacementRequest.ReplacementItem(null, 1)),
                "123 Main St", "Toronto", "CA", "M5V1A1");

        backend.dtos.responses.order.OrderResponse replacement =
                mock(backend.dtos.responses.order.OrderResponse.class);
        when(replacement.getId()).thenReturn(TestIds.uuid(99));
        when(replacementOrderService.createReplacement(any(), any(), any())).thenReturn(replacement);
        when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderIssueResponse response = service.resolveWithReplacement(issueId, actorId, req);

        assertEquals(OrderIssueState.RESOLVED_REPLACEMENT.name(), response.getState());
    }
}
