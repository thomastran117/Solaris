package backend.services.impl.support;

import backend.dtos.responses.support.TicketMessageResponse;
import backend.dtos.responses.support.TicketResponse;
import backend.events.email.EmailEvent;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class EmailServiceImplTest {

    private static final String TOPIC = "email-events";

    private KafkaTemplate<String, EmailEvent> kafkaTemplate;
    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        service = new EmailServiceImpl(kafkaTemplate, TOPIC);

        when(kafkaTemplate.send(anyString(), any(EmailEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    // ─── sendVerificationEmail ────────────────────────────────────────────────

    @Test
    void sendVerificationEmail_publishesToTopic() {
        service.sendVerificationEmail("user@example.com", "tok123");

        verify(kafkaTemplate).send(eq(TOPIC), any(EmailEvent.VerificationEmail.class));
    }

    // ─── sendDeviceVerificationEmail ──────────────────────────────────────────

    @Test
    void sendDeviceVerificationEmail_publishesToTopic() {
        service.sendDeviceVerificationEmail("user@example.com", "tok456", "Chrome", "Windows", "1.2.3.4");

        verify(kafkaTemplate).send(eq(TOPIC), any(EmailEvent.DeviceVerificationEmail.class));
    }

    // ─── sendTicketCreatedEmail ───────────────────────────────────────────────

    @Test
    void sendTicketCreatedEmail_publishesToTopic() {
        TicketResponse ticket = makeTicketResponse();

        service.sendTicketCreatedEmail("user@example.com", "Alice", ticket);

        verify(kafkaTemplate).send(eq(TOPIC), any(EmailEvent.TicketCreatedEmail.class));
    }

    // ─── sendTicketReplyEmail ─────────────────────────────────────────────────

    @Test
    void sendTicketReplyEmail_publishesToTopic() {
        TicketResponse ticket = makeTicketResponse();
        TicketMessageResponse message = new TicketMessageResponse(
                TestIds.uuid(1), TestIds.uuid(2), "Support Agent", "STAFF", "We're on it", Instant.now());

        service.sendTicketReplyEmail("user@example.com", "Alice", ticket, message);

        verify(kafkaTemplate).send(eq(TOPIC), any(EmailEvent.TicketReplyEmail.class));
    }

    // ─── sendOrderReceiptEmail ────────────────────────────────────────────────

    @Test
    void sendOrderReceiptEmail_publishesToTopic() {
        service.sendOrderReceiptEmail("user@example.com", "Alice", null);

        verify(kafkaTemplate).send(eq(TOPIC), any(EmailEvent.OrderReceiptEmail.class));
    }

    // ─── sendCreditIssuedEmail ────────────────────────────────────────────────

    @Test
    void sendCreditIssuedEmail_publishesToTopic() {
        service.sendCreditIssuedEmail("user@example.com", "Alice", 500L, "Goodwill credit");

        verify(kafkaTemplate).send(eq(TOPIC), any(EmailEvent.CreditIssuedEmail.class));
    }

    // ─── sendLowStockAlertEmail ───────────────────────────────────────────────

    @Test
    void sendLowStockAlertEmail_publishesToTopic() {
        service.sendLowStockAlertEmail("ops@example.com", "Bob",
                TestIds.uuid(1), "Widget", null, null, 2, 5, false);

        verify(kafkaTemplate).send(eq(TOPIC), any(EmailEvent.LowStockAlertEmail.class));
    }

    // ─── sendTeamInviteEmail ──────────────────────────────────────────────────

    @Test
    void sendTeamInviteEmail_publishesToTopic() {
        service.sendTeamInviteEmail("newmember@example.com", "Acme Corp", "MANAGER", "Alice", "https://app/accept");

        verify(kafkaTemplate).send(eq(TOPIC), any(EmailEvent.TeamInviteEmail.class));
    }

    // ─── sendBackInStockEmail ─────────────────────────────────────────────────

    @Test
    void sendBackInStockEmail_publishesToTopic() {
        service.sendBackInStockEmail("user@example.com", "Alice",
                TestIds.uuid(1), "Widget", null, null, "https://app/products/1");

        verify(kafkaTemplate).send(eq(TOPIC), any(EmailEvent.BackInStockEmail.class));
    }

    // ─── sendAbandonedCartEmail ───────────────────────────────────────────────

    @Test
    void sendAbandonedCartEmail_publishesToTopic() {
        List<EmailEvent.AbandonedItem> items = List.of(
                new EmailEvent.AbandonedItem(TestIds.uuid(5), "Widget", null, 1999L));

        service.sendAbandonedCartEmail("user@example.com", "Alice",
                TestIds.uuid(1), TestIds.uuid(2), items);

        verify(kafkaTemplate).send(eq(TOPIC), any(EmailEvent.AbandonedCartEmail.class));
    }

    @Test
    void sendAbandonedCartEmail_publishesCorrectPayload() {
        List<EmailEvent.AbandonedItem> items = List.of(
                new EmailEvent.AbandonedItem(TestIds.uuid(5), "Widget", "https://cdn/img.png", 1999L));

        service.sendAbandonedCartEmail("user@example.com", "Alice",
                TestIds.uuid(1), TestIds.uuid(2), items);

        ArgumentCaptor<EmailEvent> captor = ArgumentCaptor.forClass(EmailEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), captor.capture());

        EmailEvent.AbandonedCartEmail event = (EmailEvent.AbandonedCartEmail) captor.getValue();
        assertEquals("user@example.com", event.toEmail());
        assertEquals("Alice", event.firstName());
        assertEquals(TestIds.uuid(1), event.userId());
        assertEquals(TestIds.uuid(2), event.orderId());
        assertEquals(1, event.items().size());
        assertEquals(1999L, event.items().get(0).priceCents());
    }

    @Test
    void sendAbandonedCartEmail_kafkaFailure_doesNotThrow() {
        CompletableFuture<SendResult<String, EmailEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), any(EmailEvent.class))).thenReturn(failed);

        List<EmailEvent.AbandonedItem> items = List.of(
                new EmailEvent.AbandonedItem(TestIds.uuid(5), "Widget", null, 999L));

        assertDoesNotThrow(() -> service.sendAbandonedCartEmail(
                "user@example.com", "Alice", TestIds.uuid(1), TestIds.uuid(2), items));
    }

    // ─── Kafka failure is swallowed ───────────────────────────────────────────

    @Test
    void sendVerificationEmail_kafkaFailure_doesNotThrow() {
        CompletableFuture<SendResult<String, EmailEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), any(EmailEvent.class))).thenReturn(failed);

        assertDoesNotThrow(() -> service.sendVerificationEmail("user@example.com", "tok"));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private TicketResponse makeTicketResponse() {
        return new TicketResponse(
                TestIds.uuid(10), TestIds.uuid(1), "Alice Smith",
                TestIds.uuid(1), null, null, null,
                "Missing item", "I didn't receive item X",
                "OPEN", "NORMAL", "ORDER_ISSUE",
                List.of(), null, null, null, null);
    }
}
