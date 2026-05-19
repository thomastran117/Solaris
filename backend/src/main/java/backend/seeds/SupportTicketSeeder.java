package backend.seeds;

import backend.models.core.SupportTicket;
import backend.models.core.SupportTicketMessage;
import backend.models.enums.TicketCategory;
import backend.models.enums.TicketMessageAuthor;
import backend.models.enums.TicketPriority;
import backend.models.enums.TicketStatus;
import backend.repositories.SupportTicketRepository;
import backend.seeds.OrderSeeder.SeededOrders;
import backend.seeds.UserSeeder.SeededUsers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class SupportTicketSeeder {

    private final SupportTicketRepository ticketRepository;

    public void seed(SeededUsers users, SeededOrders orders) {
        if (ticketRepository.countByCustomerIdAndStatusNot(users.bob().getId(), TicketStatus.CLOSED) > 0
                || ticketRepository.countByCustomerIdAndStatusNot(users.bob().getId(), TicketStatus.OPEN) > 0) return;

        // Ticket 1: Bob — StyleHub shipping issue (CLOSED)
        SupportTicket ticket1 = new SupportTicket();
        ticket1.setCustomer(users.bob());
        ticket1.setOpenedBy(users.bob());
        ticket1.setAssignedTo(users.admin());
        ticket1.setOrder(orders.bob1());
        ticket1.setSubject("Package arrived later than expected");
        ticket1.setDescription("My order was marked as delivered 3 days after the estimated date. " +
                "Everything arrived fine but wanted to flag the delay.");
        ticket1.setStatus(TicketStatus.CLOSED);
        ticket1.setPriority(TicketPriority.LOW);
        ticket1.setCategory(TicketCategory.SHIPPING);
        ticket1.setResolvedAt(Instant.now().minus(20, ChronoUnit.DAYS));
        ticket1.setClosedAt(Instant.now().minus(20, ChronoUnit.DAYS));

        SupportTicketMessage msg1a = message(ticket1, users.bob(), TicketMessageAuthor.CUSTOMER,
                "Hi, my order (bob1) showed a delivery estimate of 3–5 business days but it arrived on day 8. " +
                "Everything was in good condition though!");
        SupportTicketMessage msg1b = message(ticket1, users.admin(), TicketMessageAuthor.STAFF,
                "Hi Bob, thanks for reaching out! I'm sorry to hear your delivery was delayed. " +
                "We've flagged this with the carrier. As a courtesy, I've added a $5 credit to your account. " +
                "Please let us know if you have any other questions!");

        ticket1.getMessages().add(msg1a);
        ticket1.getMessages().add(msg1b);
        ticketRepository.save(ticket1);

        // Ticket 2: Alice — TechGadgets product inquiry (OPEN)
        SupportTicket ticket2 = new SupportTicket();
        ticket2.setCustomer(users.alice());
        ticket2.setOpenedBy(users.alice());
        ticket2.setAssignedTo(users.admin());
        ticket2.setOrder(orders.alice4());
        ticket2.setSubject("Will my mechanical keyboard work with a KVM switch?");
        ticket2.setDescription("I ordered the Mechanical Keyboard and want to use it with a KVM switch " +
                "to share between my work and personal computer. Is this supported?");
        ticket2.setStatus(TicketStatus.OPEN);
        ticket2.setPriority(TicketPriority.NORMAL);
        ticket2.setCategory(TicketCategory.OTHER);

        SupportTicketMessage msg2a = message(ticket2, users.alice(), TicketMessageAuthor.CUSTOMER,
                "Hi, I just placed an order for the Mechanical Keyboard (order alice4). " +
                "I'd like to know if it's compatible with a standard USB KVM switch for dual-computer setups. Thanks!");

        ticket2.getMessages().add(msg2a);
        ticketRepository.save(ticket2);
    }

    private SupportTicketMessage message(SupportTicket ticket, backend.models.core.User author,
                                          TicketMessageAuthor authorRole, String body) {
        SupportTicketMessage msg = new SupportTicketMessage();
        msg.setTicket(ticket);
        msg.setAuthor(author);
        msg.setAuthorRole(authorRole);
        msg.setBody(body);
        return msg;
    }
}
