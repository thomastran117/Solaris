package backend.services.impl.support;

import backend.dtos.requests.support.AssignTicketRequest;
import backend.dtos.requests.support.CreateTicketRequest;
import backend.dtos.requests.support.TicketMessageRequest;
import backend.dtos.requests.support.UpdateTicketPriorityRequest;
import backend.dtos.requests.support.UpdateTicketStatusRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.support.TicketResponse;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Order;
import backend.models.core.SupportTicket;
import backend.models.core.SupportTicketMessage;
import backend.models.core.User;
import backend.models.enums.TicketCategory;
import backend.models.enums.TicketMessageAuthor;
import backend.models.enums.TicketPriority;
import backend.models.enums.TicketStatus;
import backend.models.enums.UserRole;
import backend.repositories.OrderRepository;
import backend.repositories.SupportTicketMessageRepository;
import backend.repositories.SupportTicketRepository;
import backend.repositories.UserRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupportTicketServiceImplTest {

    private SupportTicketRepository ticketRepository;
    private SupportTicketMessageRepository messageRepository;
    private UserRepository userRepository;
    private OrderRepository orderRepository;
    private SupportTicketServiceImpl service;

    @BeforeEach
    void setUp() {
        ticketRepository  = mock(SupportTicketRepository.class);
        messageRepository = mock(SupportTicketMessageRepository.class);
        userRepository    = mock(UserRepository.class);
        orderRepository   = mock(OrderRepository.class);
        service = new SupportTicketServiceImpl(ticketRepository, messageRepository, userRepository, orderRepository);
    }

    // ─── createTicket ─────────────────────────────────────────────────────────

    @Test
    void createTicket_customerOpensOwnTicket() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            SupportTicket t = inv.getArgument(0);
            t.setId(TestIds.uuid(100));
            t.setStatus(TicketStatus.OPEN);
            return t;
        });
        when(messageRepository.save(any())).thenAnswer(inv -> {
            SupportTicketMessage m = inv.getArgument(0);
            m.setAuthor(customer);
            m.setAuthorRole(TicketMessageAuthor.SYSTEM);
            return m;
        });
        when(messageRepository.findAllByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        CreateTicketRequest req = new CreateTicketRequest(
                "Missing item", "I didn't receive item X", TicketCategory.ORDER_ISSUE, null, null, null);

        TicketResponse resp = service.createTicket(TestIds.uuid(1), req);

        assertEquals("Missing item", resp.getSubject());
        assertEquals(TestIds.uuid(1), resp.getCustomerId());
        verify(ticketRepository).save(any());
    }

    @Test
    void createTicket_staffOpensOnBehalfOfCustomer() {
        User staff    = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(5), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(userRepository.findById(TestIds.uuid(5))).thenReturn(Optional.of(customer));
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            SupportTicket t = inv.getArgument(0);
            t.setId(TestIds.uuid(101));
            t.setStatus(TicketStatus.OPEN);
            return t;
        });
        when(messageRepository.save(any())).thenAnswer(inv -> {
            SupportTicketMessage m = inv.getArgument(0);
            m.setAuthor(staff);
            m.setAuthorRole(TicketMessageAuthor.SYSTEM);
            return m;
        });
        when(messageRepository.findAllByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        CreateTicketRequest req = new CreateTicketRequest(
                "Damaged item", "Customer reported damage", TicketCategory.PRODUCT_DEFECT, null, null, TestIds.uuid(5));

        TicketResponse resp = service.createTicket(TestIds.uuid(2), req);
        assertEquals(TestIds.uuid(5), resp.getCustomerId());
    }

    // ─── getTicket ────────────────────────────────────────────────────────────

    @Test
    void getTicket_customerCanViewOwnTicket() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), customer);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(ticketRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(ticket));
        when(messageRepository.findAllByTicketIdOrderByCreatedAtAsc(TestIds.uuid(10))).thenReturn(List.of());

        TicketResponse resp = service.getTicket(TestIds.uuid(10), TestIds.uuid(1));
        assertEquals(TestIds.uuid(10), resp.getId());
    }

    @Test
    void getTicket_customerCannotViewOtherCustomersTicket() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        User other    = makeUser(TestIds.uuid(2), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), other);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(ticketRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(ticket));

        assertThrows(ForbiddenException.class, () -> service.getTicket(TestIds.uuid(10), TestIds.uuid(1)));
    }

    @Test
    void getTicket_staffCanViewAnyTicket() {
        User staff    = makeUser(TestIds.uuid(3), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), customer);
        when(userRepository.findById(TestIds.uuid(3))).thenReturn(Optional.of(staff));
        when(ticketRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(ticket));
        when(messageRepository.findAllByTicketIdOrderByCreatedAtAsc(TestIds.uuid(10))).thenReturn(List.of());

        TicketResponse resp = service.getTicket(TestIds.uuid(10), TestIds.uuid(3));
        assertEquals(TestIds.uuid(10), resp.getId());
    }

    // ─── addMessage ───────────────────────────────────────────────────────────

    @Test
    void addMessage_staffReplyAdvancesStatusToPendingCustomer() {
        User staff    = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), customer);
        ticket.setStatus(TicketStatus.OPEN);

        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(ticketRepository.findByIdForUpdate(TestIds.uuid(10))).thenReturn(Optional.of(ticket));
        when(messageRepository.save(any())).thenAnswer(inv -> {
            SupportTicketMessage m = inv.getArgument(0);
            m.setAuthor(staff);
            return m;
        });

        service.addMessage(TestIds.uuid(10), TestIds.uuid(2), new TicketMessageRequest("We're looking into it"));

        assertEquals(TicketStatus.PENDING_CUSTOMER, ticket.getStatus());
        verify(ticketRepository).save(ticket);
    }

    @Test
    void addMessage_customerReplyAdvancesStatusToPendingInternal() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), customer);
        ticket.setStatus(TicketStatus.PENDING_CUSTOMER);

        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(ticketRepository.findByIdForUpdate(TestIds.uuid(10))).thenReturn(Optional.of(ticket));
        when(messageRepository.save(any())).thenAnswer(inv -> {
            SupportTicketMessage m = inv.getArgument(0);
            m.setAuthor(customer);
            return m;
        });

        service.addMessage(TestIds.uuid(10), TestIds.uuid(1), new TicketMessageRequest("Still waiting"));

        assertEquals(TicketStatus.PENDING_INTERNAL, ticket.getStatus());
    }

    @Test
    void addMessage_closedTicketThrows() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), customer);
        ticket.setStatus(TicketStatus.CLOSED);

        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(ticketRepository.findByIdForUpdate(TestIds.uuid(10))).thenReturn(Optional.of(ticket));

        assertThrows(backend.exceptions.http.ConflictException.class,
                () -> service.addMessage(TestIds.uuid(10), TestIds.uuid(1), new TicketMessageRequest("message")));
    }

    // ─── listTickets ──────────────────────────────────────────────────────────

    @Test
    void listTickets_staffSeeAllTickets() {
        User staff = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(ticketRepository.findAllByFilters(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PagedResponse<TicketResponse> result = service.listTickets(TestIds.uuid(2), null, null, 0, 20);
        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    void listTickets_customerSeesOnlyOwnTickets() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(ticketRepository.findAllByCustomerId(eq(TestIds.uuid(1)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PagedResponse<TicketResponse> result = service.listTickets(TestIds.uuid(1), null, null, 0, 20);
        assertNotNull(result);
        verify(ticketRepository).findAllByCustomerId(eq(TestIds.uuid(1)), any(Pageable.class));
        verify(ticketRepository, never()).findAllByFilters(any(), any(), any());
    }

    // ─── createTicket — order linking & priority ──────────────────────────────

    @Test
    void createTicket_withOrderId_linksOrderToTicket() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        Order order = makeOrder(TestIds.uuid(99), customer);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(orderRepository.findById(TestIds.uuid(99))).thenReturn(Optional.of(order));
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            SupportTicket t = inv.getArgument(0);
            t.setId(TestIds.uuid(100));
            t.setStatus(TicketStatus.OPEN);
            return t;
        });
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.findAllByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        CreateTicketRequest req = new CreateTicketRequest(
                "Order issue", "Item missing", TicketCategory.ORDER_ISSUE, null, TestIds.uuid(99), null);
        TicketResponse resp = service.createTicket(TestIds.uuid(1), req);

        assertEquals(TestIds.uuid(99), resp.getOrderId());
    }

    @Test
    void createTicket_customerLinksOtherUsersOrder_throwsForbidden() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        User otherUser = makeUser(TestIds.uuid(9), UserRole.USER);
        Order order = makeOrder(TestIds.uuid(99), otherUser); // owned by a different user
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(orderRepository.findById(TestIds.uuid(99))).thenReturn(Optional.of(order));

        CreateTicketRequest req = new CreateTicketRequest(
                "subject", "desc", TicketCategory.ORDER_ISSUE, null, TestIds.uuid(99), null);
        assertThrows(ForbiddenException.class, () -> service.createTicket(TestIds.uuid(1), req));
    }

    @Test
    void createTicket_withExplicitPriority_usesProvidedPriority() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            SupportTicket t = inv.getArgument(0);
            t.setId(TestIds.uuid(100));
            t.setStatus(TicketStatus.OPEN);
            return t;
        });
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.findAllByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        CreateTicketRequest req = new CreateTicketRequest(
                "Urgent", "Fix this now", TicketCategory.OTHER, TicketPriority.HIGH, null, null);
        TicketResponse resp = service.createTicket(TestIds.uuid(1), req);

        assertEquals("HIGH", resp.getPriority());
    }

    @Test
    void createTicket_nullPriority_defaultsToNormal() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            SupportTicket t = inv.getArgument(0);
            t.setId(TestIds.uuid(100));
            t.setStatus(TicketStatus.OPEN);
            t.setPriority(TicketPriority.NORMAL);
            return t;
        });
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.findAllByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        CreateTicketRequest req = new CreateTicketRequest(
                "subject", "desc", TicketCategory.OTHER, null, null, null);
        TicketResponse resp = service.createTicket(TestIds.uuid(1), req);

        assertEquals("NORMAL", resp.getPriority());
    }

    // ─── addMessage — additional paths ────────────────────────────────────────

    @Test
    void addMessage_nonOwnerCustomerForbidden() {
        User owner  = makeUser(TestIds.uuid(1), UserRole.USER);
        User other  = makeUser(TestIds.uuid(2), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), owner);

        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(other));
        when(ticketRepository.findByIdForUpdate(TestIds.uuid(10))).thenReturn(Optional.of(ticket));

        assertThrows(ForbiddenException.class,
                () -> service.addMessage(TestIds.uuid(10), TestIds.uuid(2), new TicketMessageRequest("hi")));
    }

    @Test
    void addMessage_pendingInternal_alwaysAdvancesToPendingCustomer() {
        // PENDING_INTERNAL → PENDING_CUSTOMER regardless of who replies
        User staff  = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), customer);
        ticket.setStatus(TicketStatus.PENDING_INTERNAL);

        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(ticketRepository.findByIdForUpdate(TestIds.uuid(10))).thenReturn(Optional.of(ticket));
        when(messageRepository.save(any())).thenAnswer(inv -> {
            SupportTicketMessage m = inv.getArgument(0);
            m.setAuthor(staff);
            return m;
        });

        service.addMessage(TestIds.uuid(10), TestIds.uuid(2), new TicketMessageRequest("update"));

        assertEquals(TicketStatus.PENDING_CUSTOMER, ticket.getStatus());
    }

    // ─── updateStatus ─────────────────────────────────────────────────────────

    @Test
    void updateStatus_nonStaffForbidden() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));

        assertThrows(ForbiddenException.class,
                () -> service.updateStatus(TestIds.uuid(10), TestIds.uuid(1),
                        new UpdateTicketStatusRequest(TicketStatus.RESOLVED)));
    }

    @Test
    void updateStatus_resolved_setsResolvedAt() {
        User staff = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), customer);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(ticketRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.findAllByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        TicketResponse resp = service.updateStatus(TestIds.uuid(10), TestIds.uuid(2),
                new UpdateTicketStatusRequest(TicketStatus.RESOLVED));

        assertNotNull(resp.getResolvedAt());
        assertNull(resp.getClosedAt());
    }

    @Test
    void updateStatus_closed_setsClosedAt() {
        User staff = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), customer);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(ticketRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.findAllByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        TicketResponse resp = service.updateStatus(TestIds.uuid(10), TestIds.uuid(2),
                new UpdateTicketStatusRequest(TicketStatus.CLOSED));

        assertNotNull(resp.getClosedAt());
        assertNull(resp.getResolvedAt());
    }

    @Test
    void addMessage_customerPostingToPendingInternalDoesNotAdvanceStatus() {
        // C15: before the fix, the || without parentheses caused any post to a
        // PENDING_INTERNAL ticket to advance status regardless of the poster's role.
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), customer);
        ticket.setStatus(TicketStatus.PENDING_INTERNAL);

        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(ticketRepository.findByIdForUpdate(TestIds.uuid(10))).thenReturn(Optional.of(ticket));
        when(messageRepository.save(any())).thenAnswer(inv -> {
            SupportTicketMessage m = inv.getArgument(0);
            m.setAuthor(customer);
            return m;
        });

        service.addMessage(TestIds.uuid(10), TestIds.uuid(1), new TicketMessageRequest("Any update?"));

        // Customer posting to PENDING_INTERNAL should NOT advance to PENDING_CUSTOMER;
        // the branch !isStaff && PENDING_CUSTOMER doesn't fire either, so status is unchanged.
        assertEquals(TicketStatus.PENDING_INTERNAL, ticket.getStatus());
    }

    @Test
    void updateStatus_closedTicketCannotBeModified() {
        // C16: CLOSED tickets must be immutable.
        User staff = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), customer);
        ticket.setStatus(TicketStatus.CLOSED);

        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(ticketRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(ticket));

        assertThrows(backend.exceptions.http.ConflictException.class,
                () -> service.updateStatus(TestIds.uuid(10), TestIds.uuid(2),
                        new UpdateTicketStatusRequest(TicketStatus.OPEN)));
    }

    @Test
    void updateStatus_revertToOpenClearsTerminalTimestamps() {
        // M11: closedAt/resolvedAt must be nulled when reverting to a non-terminal status.
        User staff = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), customer);
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolvedAt(java.time.Instant.now());

        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(ticketRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.findAllByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        service.updateStatus(TestIds.uuid(10), TestIds.uuid(2),
                new UpdateTicketStatusRequest(TicketStatus.OPEN));

        assertNull(ticket.getResolvedAt());
        assertNull(ticket.getClosedAt());
    }

    // ─── assignTicket ─────────────────────────────────────────────────────────

    @Test
    void assignTicket_happyPath_assignsStaffMemberAndSaves() {
        User actor   = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User assignee = makeUser(TestIds.uuid(3), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), customer);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(actor));
        when(userRepository.findById(TestIds.uuid(3))).thenReturn(Optional.of(assignee));
        when(ticketRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.findAllByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        TicketResponse resp = service.assignTicket(TestIds.uuid(10), TestIds.uuid(2),
                new AssignTicketRequest(TestIds.uuid(3)));

        assertEquals(TestIds.uuid(3), resp.getAssignedToId());
        verify(ticketRepository).save(ticket);
    }

    @Test
    void assignTicket_nonStaffActorForbidden() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));

        assertThrows(ForbiddenException.class,
                () -> service.assignTicket(TestIds.uuid(10), TestIds.uuid(1),
                        new AssignTicketRequest(TestIds.uuid(3))));
    }

    @Test
    void assignTicket_assigneeNotStaff_throwsForbidden() {
        User actor   = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User assignee = makeUser(TestIds.uuid(9), UserRole.USER); // not staff
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), customer);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(actor));
        when(userRepository.findById(TestIds.uuid(9))).thenReturn(Optional.of(assignee));
        when(ticketRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(ticket));

        assertThrows(ForbiddenException.class,
                () -> service.assignTicket(TestIds.uuid(10), TestIds.uuid(2),
                        new AssignTicketRequest(TestIds.uuid(9))));
    }

    // ─── updatePriority ───────────────────────────────────────────────────────

    @Test
    void updatePriority_happyPath_updatesPriorityAndSaves() {
        User staff = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        SupportTicket ticket = makeTicket(TestIds.uuid(10), customer);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(ticketRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.findAllByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        TicketResponse resp = service.updatePriority(TestIds.uuid(10), TestIds.uuid(2),
                new UpdateTicketPriorityRequest(TicketPriority.URGENT));

        assertEquals("URGENT", resp.getPriority());
        verify(ticketRepository).save(ticket);
    }

    @Test
    void updatePriority_nonStaffForbidden() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));

        assertThrows(ForbiddenException.class,
                () -> service.updatePriority(TestIds.uuid(10), TestIds.uuid(1),
                        new UpdateTicketPriorityRequest(TicketPriority.LOW)));
    }

    // ─── getTicket — not found ────────────────────────────────────────────────

    @Test
    void getTicket_notFound_throwsResourceNotFound() {
        User staff = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(ticketRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getTicket(TestIds.uuid(99), TestIds.uuid(2)));
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

    private SupportTicket makeTicket(UUID id, User customer) {
        SupportTicket t = new SupportTicket();
        t.setId(id);
        t.setCustomer(customer);
        t.setOpenedBy(customer);
        t.setSubject("Test ticket");
        t.setDescription("Description");
        t.setStatus(TicketStatus.OPEN);
        t.setPriority(TicketPriority.NORMAL);
        t.setCategory(TicketCategory.OTHER);
        return t;
    }
}
