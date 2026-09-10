package backend.services.impl.payments;

import backend.models.core.DisputeCase;
import backend.models.core.DisputeEvidence;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.OrderStatusHistory;
import backend.models.core.SupportTicket;
import backend.models.core.SupportTicketMessage;
import backend.models.enums.DisputeEvidenceType;
import backend.repositories.OrderStatusHistoryRepository;
import backend.repositories.SupportTicketMessageRepository;
import backend.repositories.SupportTicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Assembles the evidence pack for a chargeback out of data the platform already stores: the order
 * itself, its shipping snapshot, tracking checkpoints, and the support thread.
 *
 * <p>Everything produced is <b>plain text</b>, never HTML — support staff paste these blocks
 * straight into Stripe's evidence form.
 *
 * <p>Every section is individually guarded. A missing association or a repository failure degrades
 * that one section to nothing; it must never abort case creation, because losing the case means
 * losing the evidence deadline, which loses the dispute.
 */
@Component
public class DisputeEvidenceBuilder {

    private static final Logger log = LoggerFactory.getLogger(DisputeEvidenceBuilder.class);

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneId.of("UTC"));

    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final SupportTicketMessageRepository supportTicketMessageRepository;

    public DisputeEvidenceBuilder(OrderStatusHistoryRepository orderStatusHistoryRepository,
                                  SupportTicketRepository supportTicketRepository,
                                  SupportTicketMessageRepository supportTicketMessageRepository) {
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.supportTicketMessageRepository = supportTicketMessageRepository;
    }

    /**
     * Builds every available evidence entry for {@code order}, already attached to
     * {@code disputeCase}. Returns an empty list rather than throwing when nothing can be built.
     *
     * <p>Order items, the shipping address and tracking checkpoints come first — those three are
     * the minimum a dispute submission needs.
     */
    public List<DisputeEvidence> buildEvidence(DisputeCase disputeCase, Order order) {
        List<DisputeEvidence> out = new ArrayList<>();
        if (order == null) return out;

        addIfPresent(out, disputeCase, DisputeEvidenceType.ORDER_DETAILS,
                () -> orderDetails(order), "order details");
        addIfPresent(out, disputeCase, DisputeEvidenceType.ORDER_DETAILS,
                () -> shippingAddress(order), "shipping address");
        addIfPresent(out, disputeCase, DisputeEvidenceType.TRACKING,
                () -> tracking(order), "tracking");
        addIfPresent(out, disputeCase, DisputeEvidenceType.DELIVERY_CONFIRMATION,
                () -> deliveryConfirmation(order), "delivery confirmation");
        addIfPresent(out, disputeCase, DisputeEvidenceType.CUSTOMER_COMMUNICATION,
                () -> customerCommunication(order), "customer communication");

        return out;
    }

    // -------------------------------------------------------------------------
    // Sections
    // -------------------------------------------------------------------------

    private String orderDetails(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("ORDER ").append(order.getId()).append('\n');
        sb.append("Placed: ").append(fmt(order.getCreatedAt())).append('\n');
        if (order.getPaidAt() != null) sb.append("Paid: ").append(fmt(order.getPaidAt())).append('\n');
        sb.append("Status: ").append(order.getStatus()).append('\n');
        if (order.getPaymentIntentId() != null) {
            sb.append("Payment intent: ").append(order.getPaymentIntentId()).append('\n');
        }

        sb.append("\nITEMS\n");
        List<OrderItem> items = order.getItems();
        if (items == null || items.isEmpty()) {
            sb.append("  (no line items recorded)\n");
        } else {
            for (OrderItem item : items) {
                String name = item.getBundleName() != null ? item.getBundleName()
                        : item.getKitName() != null ? item.getKitName()
                        : item.getProductName();
                sb.append("  - ").append(name);
                if (item.getVariantTitle() != null) sb.append(" (").append(item.getVariantTitle()).append(')');
                if (item.getVariantSku() != null) sb.append(" [SKU ").append(item.getVariantSku()).append(']');
                sb.append(" x").append(item.getQuantity());
                if (item.getUnitPrice() != null) {
                    sb.append(" @ ").append(item.getUnitPrice()).append(' ').append(currency(order));
                }
                sb.append('\n');
            }
        }

        sb.append("\nTOTALS\n");
        sb.append("  Order total: ").append(order.getTotalAmount()).append(' ').append(currency(order)).append('\n');
        if (order.getTaxAmount() != null) {
            sb.append("  Tax: ").append(order.getTaxAmount()).append(' ').append(currency(order)).append('\n');
        }
        if (order.getShippingCostCents() > 0) {
            sb.append("  Shipping: ").append(String.format("%.2f", order.getShippingCostCents() / 100.0))
              .append(' ').append(currency(order)).append('\n');
        }
        if (order.getRefundedAmountCents() > 0) {
            // Stripe weighs a prior refund heavily — surface it rather than making support dig.
            sb.append("  Already refunded: ")
              .append(String.format("%.2f", order.getRefundedAmountCents() / 100.0))
              .append(' ').append(currency(order)).append('\n');
        }
        return sb.toString();
    }

    private String shippingAddress(Order order) {
        if (order.getShipStreet() == null && order.getShipCity() == null
                && order.getShipPostalCode() == null) {
            return null; // pickup order, or no address snapshot taken
        }
        StringBuilder sb = new StringBuilder("SHIPPING ADDRESS (snapshot at checkout)\n");
        appendIf(sb, "  ", order.getShipRecipientName());
        appendIf(sb, "  ", order.getShipStreet());
        appendIf(sb, "  ", order.getShipStreet2());
        String cityLine = joinNonBlank(" ", order.getShipCity(), order.getShipState(), order.getShipPostalCode());
        appendIf(sb, "  ", cityLine);
        appendIf(sb, "  ", order.getShipCountry());
        appendIf(sb, "  Phone: ", order.getShipPhoneNumber());
        return sb.toString();
    }

    private String tracking(Order order) {
        List<OrderStatusHistory> history =
                orderStatusHistoryRepository.findAllByOrderIdOrderByOccurredAtAsc(order.getId());
        boolean hasCarrier = order.getTrackingNumber() != null || order.getCarrier() != null;
        if (!hasCarrier && (history == null || history.isEmpty())) return null;

        StringBuilder sb = new StringBuilder("SHIPMENT\n");
        if (order.getCarrier() != null)        sb.append("  Carrier: ").append(order.getCarrier()).append('\n');
        if (order.getTrackingNumber() != null) sb.append("  Tracking number: ").append(order.getTrackingNumber()).append('\n');
        if (order.getShippedAt() != null)      sb.append("  Shipped: ").append(fmt(order.getShippedAt())).append('\n');

        if (history != null && !history.isEmpty()) {
            sb.append("\nCHECKPOINTS\n");
            for (OrderStatusHistory h : history) {
                sb.append("  ").append(fmt(h.getOccurredAt())).append(" — ").append(h.getEventType());
                if (h.getStatus() != null) sb.append(" (").append(h.getStatus()).append(')');
                if (h.getNote() != null && !h.getNote().isBlank()) sb.append(": ").append(h.getNote());
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String deliveryConfirmation(Order order) {
        if (order.getDeliveredAt() == null) return null;
        StringBuilder sb = new StringBuilder("DELIVERY CONFIRMATION\n");
        sb.append("  Delivered: ").append(fmt(order.getDeliveredAt())).append('\n');
        if (order.getShippedAt() != null) sb.append("  Shipped: ").append(fmt(order.getShippedAt())).append('\n');
        if (order.getCarrier() != null)   sb.append("  Carrier: ").append(order.getCarrier()).append('\n');
        if (order.getTrackingNumber() != null) {
            sb.append("  Tracking number: ").append(order.getTrackingNumber()).append('\n');
        }
        if (order.getPickupLocationName() != null) {
            sb.append("  Collected from: ").append(order.getPickupLocationName()).append('\n');
        }
        return sb.toString();
    }

    private String customerCommunication(Order order) {
        List<SupportTicket> tickets = supportTicketRepository.findAllByOrderIdOrderByCreatedAtAsc(order.getId());
        if (tickets == null || tickets.isEmpty()) return null;

        StringBuilder sb = new StringBuilder("SUPPORT HISTORY\n");
        for (SupportTicket t : tickets) {
            sb.append("\nTicket ").append(t.getId()).append(" — ").append(t.getSubject()).append('\n');
            sb.append("  Opened: ").append(fmt(t.getCreatedAt()))
              .append("  Status: ").append(t.getStatus()).append('\n');
            if (t.getDescription() != null && !t.getDescription().isBlank()) {
                sb.append("  ").append(t.getDescription()).append('\n');
            }
            List<SupportTicketMessage> messages =
                    supportTicketMessageRepository.findAllByTicketIdOrderByCreatedAtAsc(t.getId());
            for (SupportTicketMessage m : messages) {
                sb.append("  [").append(fmt(m.getCreatedAt())).append("] ")
                  .append(m.getAuthorRole()).append(": ").append(m.getBody()).append('\n');
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Runs one section and appends it when it produced content. A throwing section is logged and
     * skipped — partial evidence beats no case at all.
     */
    private void addIfPresent(List<DisputeEvidence> out, DisputeCase disputeCase,
                              DisputeEvidenceType type, Supplier<String> section, String label) {
        try {
            String content = section.get();
            if (content == null || content.isBlank()) return;
            DisputeEvidence e = new DisputeEvidence();
            e.setDisputeCase(disputeCase);
            e.setEvidenceType(type);
            e.setContent(content);
            out.add(e);
        } catch (Exception ex) {
            log.warn("Skipping '{}' evidence for dispute {} — {}",
                    label, disputeCase.getStripeDisputeId(), ex.toString());
        }
    }

    private String currency(Order order) {
        return order.getCurrency() != null ? order.getCurrency().toUpperCase() : "USD";
    }

    private String fmt(java.time.Instant instant) {
        return instant != null ? TS.format(instant) : "—";
    }

    private void appendIf(StringBuilder sb, String prefix, String value) {
        if (value != null && !value.isBlank()) sb.append(prefix).append(value).append('\n');
    }

    private String joinNonBlank(String separator, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(separator);
            sb.append(p);
        }
        return sb.toString();
    }
}
