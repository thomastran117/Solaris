package backend.services.impl.payments;

import backend.models.core.DisputeCase;
import backend.models.core.DisputeEvidence;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.OrderStatusHistory;
import backend.models.core.SupportTicket;
import backend.models.core.SupportTicketMessage;
import backend.models.enums.DisputeEvidenceType;
import backend.models.enums.OrderHistoryEventType;
import backend.models.enums.OrderStatus;
import backend.models.enums.TicketMessageAuthor;
import backend.models.enums.TicketStatus;
import backend.repositories.OrderStatusHistoryRepository;
import backend.repositories.SupportTicketMessageRepository;
import backend.repositories.SupportTicketRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DisputeEvidenceBuilderTest {

    private static final UUID ORDER_ID = TestIds.uuid(1);
    private static final UUID TICKET_ID = TestIds.uuid(2);

    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    private SupportTicketRepository supportTicketRepository;
    private SupportTicketMessageRepository supportTicketMessageRepository;
    private DisputeEvidenceBuilder builder;

    @BeforeEach
    void setUp() {
        orderStatusHistoryRepository = mock(OrderStatusHistoryRepository.class);
        supportTicketRepository = mock(SupportTicketRepository.class);
        supportTicketMessageRepository = mock(SupportTicketMessageRepository.class);
        builder = new DisputeEvidenceBuilder(orderStatusHistoryRepository,
                supportTicketRepository, supportTicketMessageRepository);

        when(orderStatusHistoryRepository.findAllByOrderIdOrderByOccurredAtAsc(any())).thenReturn(List.of());
        when(supportTicketRepository.findAllByOrderIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(supportTicketMessageRepository.findAllByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
    }

    /** Acceptance criterion 2: items, shipping address and tracking checkpoints at minimum. */
    @Test
    void shouldIncludeItemsAddressAndCheckpointsWhenAllDataIsPresent() {
        Order order = fullOrder();
        when(orderStatusHistoryRepository.findAllByOrderIdOrderByOccurredAtAsc(ORDER_ID))
                .thenReturn(List.of(checkpoint(OrderHistoryEventType.STATUS_CHANGED, "Picked up by carrier")));

        String all = joined(builder.buildEvidence(disputeCase(), order));

        assertTrue(all.contains("Noise-Cancelling Headphones"), "order items");
        assertTrue(all.contains("x2"), "item quantity");
        assertTrue(all.contains("Ada Lovelace"), "shipping recipient");
        assertTrue(all.contains("12 Analytical Way"), "shipping street");
        assertTrue(all.contains("London EC EC1A 1BB"), "city/state/postcode line");
        assertTrue(all.contains("UPS"), "carrier");
        assertTrue(all.contains("1Z999AA10123456784"), "tracking number");
        assertTrue(all.contains("Picked up by carrier"), "tracking checkpoint note");
    }

    @Test
    void shouldTagSectionsWithTheMatchingEvidenceTypeWhenBuilt() {
        Order order = fullOrder();
        order.setDeliveredAt(Instant.parse("2026-07-20T09:00:00Z"));
        when(orderStatusHistoryRepository.findAllByOrderIdOrderByOccurredAtAsc(ORDER_ID))
                .thenReturn(List.of(checkpoint(OrderHistoryEventType.STATUS_CHANGED, "Delivered")));
        when(supportTicketRepository.findAllByOrderIdOrderByCreatedAtAsc(ORDER_ID))
                .thenReturn(List.of(ticket()));

        Map<DisputeEvidenceType, Long> byType = builder.buildEvidence(disputeCase(), order).stream()
                .collect(Collectors.groupingBy(DisputeEvidence::getEvidenceType, Collectors.counting()));

        assertEquals(2L, byType.get(DisputeEvidenceType.ORDER_DETAILS)); // order + address
        assertEquals(1L, byType.get(DisputeEvidenceType.TRACKING));
        assertEquals(1L, byType.get(DisputeEvidenceType.DELIVERY_CONFIRMATION));
        assertEquals(1L, byType.get(DisputeEvidenceType.CUSTOMER_COMMUNICATION));
    }

    @Test
    void shouldStillProduceOrderDetailsWhenNoTrackingOrTicketsExist() {
        Order order = fullOrder();
        order.setCarrier(null);
        order.setTrackingNumber(null);
        order.setDeliveredAt(null);

        List<DisputeEvidence> evidence = builder.buildEvidence(disputeCase(), order);

        assertTrue(evidence.stream().anyMatch(e -> e.getEvidenceType() == DisputeEvidenceType.ORDER_DETAILS));
        assertFalse(evidence.stream().anyMatch(e -> e.getEvidenceType() == DisputeEvidenceType.TRACKING));
        assertFalse(evidence.stream()
                .anyMatch(e -> e.getEvidenceType() == DisputeEvidenceType.DELIVERY_CONFIRMATION));
        assertFalse(evidence.stream()
                .anyMatch(e -> e.getEvidenceType() == DisputeEvidenceType.CUSTOMER_COMMUNICATION));
    }

    @Test
    void shouldOmitShippingSectionWhenOrderHasNoAddressSnapshot() {
        Order order = fullOrder();
        order.setShipRecipientName(null);
        order.setShipStreet(null);
        order.setShipCity(null);
        order.setShipState(null);
        order.setShipPostalCode(null);
        order.setShipCountry(null);

        String all = joined(builder.buildEvidence(disputeCase(), order));

        assertFalse(all.contains("SHIPPING ADDRESS"));
        assertTrue(all.contains("ORDER "));
    }

    @Test
    void shouldNoteExistingRefundWhenOrderWasPartiallyRefunded() {
        Order order = fullOrder();
        order.setRefundedAmountCents(1500L);

        assertTrue(joined(builder.buildEvidence(disputeCase(), order)).contains("Already refunded: 15.00"));
    }

    @Test
    void shouldHandleOrderWithNoLineItemsWithoutFailing() {
        Order order = fullOrder();
        order.setItems(List.of());

        assertTrue(joined(builder.buildEvidence(disputeCase(), order)).contains("(no line items recorded)"));
    }

    /** A failing section must degrade to nothing, never abort the case. */
    @Test
    void shouldSkipFailingSectionWhenARepositoryThrows() {
        Order order = fullOrder();
        when(orderStatusHistoryRepository.findAllByOrderIdOrderByOccurredAtAsc(ORDER_ID))
                .thenThrow(new IllegalStateException("history table unavailable"));

        List<DisputeEvidence> evidence = builder.buildEvidence(disputeCase(), order);

        assertFalse(evidence.isEmpty(), "other sections must still be built");
        assertFalse(evidence.stream().anyMatch(e -> e.getEvidenceType() == DisputeEvidenceType.TRACKING));
        assertTrue(evidence.stream().anyMatch(e -> e.getEvidenceType() == DisputeEvidenceType.ORDER_DETAILS));
    }

    @Test
    void shouldReturnEmptyListWhenThereIsNoOrderToBuildFrom() {
        assertTrue(builder.buildEvidence(disputeCase(), null).isEmpty());
    }

    @Test
    void shouldAttachEveryEntryToTheDisputeCaseWhenBuilt() {
        DisputeCase c = disputeCase();

        List<DisputeEvidence> evidence = builder.buildEvidence(c, fullOrder());

        assertTrue(evidence.stream().allMatch(e -> e.getDisputeCase() == c));
    }

    @Test
    void shouldIncludeTicketMessagesWhenCustomerContactedSupport() {
        when(supportTicketRepository.findAllByOrderIdOrderByCreatedAtAsc(ORDER_ID))
                .thenReturn(List.of(ticket()));
        when(supportTicketMessageRepository.findAllByTicketIdOrderByCreatedAtAsc(TICKET_ID))
                .thenReturn(List.of(message("Where is my order?")));

        String all = joined(builder.buildEvidence(disputeCase(), fullOrder()));

        assertTrue(all.contains("Package never arrived"), "ticket subject");
        assertTrue(all.contains("Where is my order?"), "ticket message body");
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private String joined(List<DisputeEvidence> evidence) {
        return evidence.stream().map(DisputeEvidence::getContent).collect(Collectors.joining("\n"));
    }

    private DisputeCase disputeCase() {
        DisputeCase c = new DisputeCase();
        c.setStripeDisputeId("dp_1");
        return c;
    }

    private Order fullOrder() {
        Order o = new Order();
        o.setId(ORDER_ID);
        o.setStatus(OrderStatus.PAID);
        o.setCurrency("USD");
        o.setTotalAmount(new BigDecimal("249.98"));
        o.setTaxAmount(new BigDecimal("20.00"));
        o.setCreatedAt(Instant.parse("2026-07-15T10:00:00Z"));
        o.setPaidAt(Instant.parse("2026-07-15T10:01:00Z"));
        o.setPaymentIntentId("pi_1");

        o.setShipRecipientName("Ada Lovelace");
        o.setShipStreet("12 Analytical Way");
        o.setShipCity("London");
        o.setShipState("EC");
        o.setShipPostalCode("EC1A 1BB");
        o.setShipCountry("GB");

        o.setCarrier("UPS");
        o.setTrackingNumber("1Z999AA10123456784");
        o.setShippedAt(Instant.parse("2026-07-16T08:00:00Z"));

        OrderItem item = new OrderItem();
        item.setProductName("Noise-Cancelling Headphones");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("114.99"));
        o.setItems(List.of(item));

        return o;
    }

    private OrderStatusHistory checkpoint(OrderHistoryEventType type, String note) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOrderId(ORDER_ID);
        h.setEventType(type);
        h.setStatus(OrderStatus.SHIPPED);
        h.setOccurredAt(Instant.parse("2026-07-16T09:00:00Z"));
        h.setNote(note);
        return h;
    }

    private SupportTicket ticket() {
        SupportTicket t = new SupportTicket();
        t.setId(TICKET_ID);
        t.setSubject("Package never arrived");
        t.setDescription("Customer reports non-delivery.");
        t.setStatus(TicketStatus.OPEN);
        t.setCreatedAt(Instant.parse("2026-07-25T10:00:00Z"));
        return t;
    }

    private SupportTicketMessage message(String body) {
        SupportTicketMessage m = new SupportTicketMessage();
        m.setBody(body);
        m.setAuthorRole(TicketMessageAuthor.CUSTOMER);
        m.setCreatedAt(Instant.parse("2026-07-25T10:05:00Z"));
        return m;
    }
}
