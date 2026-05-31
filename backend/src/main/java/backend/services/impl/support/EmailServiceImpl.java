package backend.services.impl.support;

import backend.dtos.responses.order.OrderResponse;
import backend.dtos.responses.support.TicketMessageResponse;
import backend.dtos.responses.support.TicketResponse;
import backend.events.email.EmailEvent;
import backend.services.intf.support.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final KafkaTemplate<String, EmailEvent> kafkaTemplate;
    private final String topic;

    public EmailServiceImpl(
            KafkaTemplate<String, EmailEvent> kafkaTemplate,
            @Value("${app.kafka.topics.email-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void sendVerificationEmail(String toEmail, String token) {
        publish(new EmailEvent.VerificationEmail(toEmail, token));
    }

    @Override
    public void sendDeviceVerificationEmail(String toEmail, String token,
                                            String browser, String os, String ip) {
        publish(new EmailEvent.DeviceVerificationEmail(toEmail, token, browser, os, ip));
    }

    @Override
    public void sendOrderReceiptEmail(String toEmail, String firstName, OrderResponse order) {
        publish(new EmailEvent.OrderReceiptEmail(toEmail, firstName, order));
    }

    @Override
    public void sendLowStockAlertEmail(String toEmail, String firstName,
                                       java.util.UUID productId, String productName,
                                       java.util.UUID variantId, String variantSku,
                                       int currentStock, Integer threshold,
                                       boolean outOfStock) {
        publish(new EmailEvent.LowStockAlertEmail(toEmail, firstName, productId, productName,
                variantId, variantSku, currentStock, threshold, outOfStock));
    }

    @Override
    public void sendTicketCreatedEmail(String toEmail, String firstName, TicketResponse ticket) {
        publish(new EmailEvent.TicketCreatedEmail(toEmail, firstName, ticket));
    }

    @Override
    public void sendTicketReplyEmail(String toEmail, String firstName, TicketResponse ticket,
                                     TicketMessageResponse message) {
        publish(new EmailEvent.TicketReplyEmail(toEmail, firstName, ticket, message));
    }

    @Override
    public void sendCreditIssuedEmail(String toEmail, String firstName, long amountCents, String reason) {
        publish(new EmailEvent.CreditIssuedEmail(toEmail, firstName, amountCents, reason));
    }

    @Override
    public void sendReplacementOrderEmail(String toEmail, String firstName, OrderResponse replacementOrder) {
        publish(new EmailEvent.ReplacementOrderEmail(toEmail, firstName, replacementOrder));
    }

    @Override
    public void sendBackInStockEmail(String toEmail, String firstName,
                                     java.util.UUID productId, String productName,
                                     java.util.UUID variantId, String variantTitle,
                                     String productUrl) {
        publish(new EmailEvent.BackInStockEmail(toEmail, firstName, productId, productName,
                variantId, variantTitle, productUrl));
    }

    @Override
    public void sendTeamInviteEmail(String toEmail, String companyName, String role,
                                    String inviterDisplayName, String acceptUrl) {
        publish(new EmailEvent.TeamInviteEmail(toEmail, companyName, role, inviterDisplayName, acceptUrl));
    }

    @Override
    public void sendAbandonedCartEmail(String toEmail, String firstName,
                                       java.util.UUID userId, java.util.UUID orderId,
                                       java.util.List<EmailEvent.AbandonedItem> items) {
        publish(new EmailEvent.AbandonedCartEmail(toEmail, firstName, userId, orderId, items));
    }

    @Override
    public void sendQuestionPostedEmail(java.util.UUID vendorUserId, String toEmail,
                                        String vendorFirstName, String productName,
                                        String questionText, java.util.UUID questionId) {
        publish(new EmailEvent.QuestionPostedEmail(vendorUserId, toEmail, vendorFirstName,
                productName, questionText, questionId));
    }

    @Override
    public void sendGiftCardIssuedEmail(String toEmail, String firstName,
                                        String giftCardCode, int originalValueCents,
                                        String companyName) {
        publish(new EmailEvent.GiftCardIssuedEmail(toEmail, firstName, giftCardCode,
                originalValueCents, companyName));
    }

    @Override
    public void sendPriceDropEmail(String toEmail, java.util.UUID userId, String productName,
                                   String productUrl, int oldPriceCents, int newPriceCents) {
        publish(new EmailEvent.PriceDropEmail(userId, toEmail, productName, productUrl,
                oldPriceCents, newPriceCents));
    }

    private void publish(EmailEvent event) {
        kafkaTemplate.send(topic, event).whenComplete((res, ex) -> {
            if (ex != null) {
                log.warn("email-events publish failed type={}", event.getClass().getSimpleName(), ex);
            }
        });
    }
}
