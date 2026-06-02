package backend.kafka.workers;

import backend.configurations.environment.EnvironmentSetting;
import backend.dtos.responses.order.OrderItemResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.dtos.responses.support.TicketMessageResponse;
import backend.dtos.responses.support.TicketResponse;
import backend.events.email.EmailEvent;
import backend.models.enums.AnnouncementType;
import backend.testutil.TestIds;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.support.RetryTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailSenderTest {

    private static final UUID USER_ID    = TestIds.uuid(1);
    private static final UUID ORDER_ID   = TestIds.uuid(2);
    private static final UUID PRODUCT_ID = TestIds.uuid(3);
    private static final UUID COMPANY_ID = TestIds.uuid(4);

    private JavaMailSender mailSender;
    private EmailSender    emailSender;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        Session session = Session.getInstance(new Properties());
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(session));

        EnvironmentSetting env = new EnvironmentSetting();
        env.getEmail().setFrom("noreply@shopwave.com");
        env.getEmail().setVerificationBaseUrl("https://shopwave.com");

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new org.springframework.retry.policy.SimpleRetryPolicy(1));

        emailSender = new EmailSender(mailSender, env, retryTemplate);
    }

    // ─── Route dispatch: each event type calls mailSender.send() ─────────────

    @Test
    void send_verificationEmail_callsMailSend() {
        emailSender.send(new EmailEvent.VerificationEmail("user@example.com", "tok123"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_deviceVerificationEmail_callsMailSend() {
        emailSender.send(new EmailEvent.DeviceVerificationEmail(
                "user@example.com", "tok456", "Chrome", "macOS", "1.2.3.4"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_orderReceiptEmail_callsMailSend() {
        OrderItemResponse item = mock(OrderItemResponse.class);
        when(item.getProductName()).thenReturn("Widget");
        when(item.getBundleName()).thenReturn(null);
        when(item.getVariantTitle()).thenReturn(null);
        when(item.getQuantity()).thenReturn(1);
        when(item.getUnitPrice()).thenReturn(new BigDecimal("49.99"));

        OrderResponse order = mock(OrderResponse.class);
        when(order.getId()).thenReturn(ORDER_ID);
        when(order.getStatus()).thenReturn("PAID");
        when(order.getTotalAmount()).thenReturn(new BigDecimal("49.99"));
        when(order.getCurrency()).thenReturn("USD");
        when(order.getCreatedAt()).thenReturn(Instant.now());
        when(order.getItems()).thenReturn(List.of(item));

        assertDoesNotThrow(() ->
                emailSender.send(new EmailEvent.OrderReceiptEmail("user@example.com", "Alice", order)));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_lowStockAlertEmail_lowStock_callsMailSend() {
        emailSender.send(new EmailEvent.LowStockAlertEmail(
                "vendor@example.com", "Bob", PRODUCT_ID, "Widget",
                null, null, 3, 5, false));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_lowStockAlertEmail_outOfStock_callsMailSend() {
        emailSender.send(new EmailEvent.LowStockAlertEmail(
                "vendor@example.com", "Bob", PRODUCT_ID, "Widget",
                null, null, 0, 5, true));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_ticketCreatedEmail_callsMailSend() {
        TicketResponse ticket = mock(TicketResponse.class);
        when(ticket.getId()).thenReturn(TestIds.uuid(42));
        when(ticket.getSubject()).thenReturn("Order issue");

        emailSender.send(new EmailEvent.TicketCreatedEmail("user@example.com", "Carol", ticket));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_ticketReplyEmail_callsMailSend() {
        TicketResponse ticket = mock(TicketResponse.class);
        when(ticket.getId()).thenReturn(TestIds.uuid(42));
        when(ticket.getSubject()).thenReturn("Order issue");
        TicketMessageResponse message = mock(TicketMessageResponse.class);
        when(message.getBody()).thenReturn("We resolved it");

        emailSender.send(new EmailEvent.TicketReplyEmail("user@example.com", "Carol", ticket, message));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_creditIssuedEmail_withReason_callsMailSend() {
        emailSender.send(new EmailEvent.CreditIssuedEmail(
                "user@example.com", "Dave", 1500L, "Compensation for delayed order"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_creditIssuedEmail_nullNameAndReason_callsMailSend() {
        emailSender.send(new EmailEvent.CreditIssuedEmail("user@example.com", null, 500L, null));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_replacementOrderEmail_callsMailSend() {
        OrderResponse replacement = mock(OrderResponse.class);
        when(replacement.getId()).thenReturn(TestIds.uuid(10));

        emailSender.send(new EmailEvent.ReplacementOrderEmail("user@example.com", "Eve", replacement));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_backInStockEmail_callsMailSend() {
        emailSender.send(new EmailEvent.BackInStockEmail(
                "user@example.com", "Frank", PRODUCT_ID, "Widget",
                null, null, "https://shopwave.com/products/123"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_teamInviteEmail_callsMailSend() {
        emailSender.send(new EmailEvent.TeamInviteEmail(
                "invite@example.com", "Acme Corp", "MANAGER",
                "Grace", "https://shopwave.com/invite/abc"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_announcementEmail_callsMailSend() {
        emailSender.send(new EmailEvent.AnnouncementEmail(
                "follower@example.com", "Hank", COMPANY_ID, "Acme",
                TestIds.uuid(5), "Summer Sale!", "50% off everything", AnnouncementType.SALE));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_abandonedCartEmail_callsMailSend() {
        EmailEvent.AbandonedItem item = new EmailEvent.AbandonedItem(
                PRODUCT_ID, "Widget", null, 2999L);

        emailSender.send(new EmailEvent.AbandonedCartEmail(
                "user@example.com", "Ivy", USER_ID, ORDER_ID, List.of(item)));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_questionPostedEmail_callsMailSend() {
        emailSender.send(new EmailEvent.QuestionPostedEmail(
                USER_ID, "vendor@example.com", "Jack", "Widget Pro",
                "Does it come in blue?", TestIds.uuid(6)));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_giftCardIssuedEmail_callsMailSend() {
        emailSender.send(new EmailEvent.GiftCardIssuedEmail(
                "user@example.com", "Kim", "GC-ABC-123", 5000, "Acme Store"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_priceDropEmail_callsMailSend() {
        emailSender.send(new EmailEvent.PriceDropEmail(
                USER_ID, "user@example.com", "Widget Pro",
                "https://shopwave.com/products/456", 9999, 7999));

        verify(mailSender).send(any(MimeMessage.class));
    }
}
