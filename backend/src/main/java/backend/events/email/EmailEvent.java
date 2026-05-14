package backend.events.email;

import backend.dtos.responses.order.OrderResponse;
import backend.dtos.responses.support.TicketMessageResponse;
import backend.dtos.responses.support.TicketResponse;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = EmailEvent.VerificationEmail.class,       name = "VERIFICATION"),
    @JsonSubTypes.Type(value = EmailEvent.DeviceVerificationEmail.class, name = "DEVICE_VERIFICATION"),
    @JsonSubTypes.Type(value = EmailEvent.OrderReceiptEmail.class,       name = "ORDER_RECEIPT"),
    @JsonSubTypes.Type(value = EmailEvent.LowStockAlertEmail.class,      name = "LOW_STOCK_ALERT"),
    @JsonSubTypes.Type(value = EmailEvent.TicketCreatedEmail.class,      name = "TICKET_CREATED"),
    @JsonSubTypes.Type(value = EmailEvent.TicketReplyEmail.class,        name = "TICKET_REPLY"),
    @JsonSubTypes.Type(value = EmailEvent.CreditIssuedEmail.class,       name = "CREDIT_ISSUED"),
    @JsonSubTypes.Type(value = EmailEvent.ReplacementOrderEmail.class,   name = "REPLACEMENT_ORDER"),
    @JsonSubTypes.Type(value = EmailEvent.BackInStockEmail.class,        name = "BACK_IN_STOCK"),
    @JsonSubTypes.Type(value = EmailEvent.TeamInviteEmail.class,         name = "TEAM_INVITE"),
})
public sealed interface EmailEvent
        permits EmailEvent.VerificationEmail,
                EmailEvent.DeviceVerificationEmail,
                EmailEvent.OrderReceiptEmail,
                EmailEvent.LowStockAlertEmail,
                EmailEvent.TicketCreatedEmail,
                EmailEvent.TicketReplyEmail,
                EmailEvent.CreditIssuedEmail,
                EmailEvent.ReplacementOrderEmail,
                EmailEvent.BackInStockEmail,
                EmailEvent.TeamInviteEmail {

    record VerificationEmail(
        String toEmail,
        String token
    ) implements EmailEvent {}

    record DeviceVerificationEmail(
        String toEmail,
        String token,
        String browser,
        String os,
        String ip
    ) implements EmailEvent {}

    record OrderReceiptEmail(
        String toEmail,
        String firstName,
        OrderResponse order
    ) implements EmailEvent {}

    record LowStockAlertEmail(
        String toEmail,
        String firstName,
        long productId,
        String productName,
        Long variantId,
        String variantSku,
        int currentStock,
        Integer threshold,
        boolean outOfStock
    ) implements EmailEvent {}

    record TicketCreatedEmail(
        String toEmail,
        String firstName,
        TicketResponse ticket
    ) implements EmailEvent {}

    record TicketReplyEmail(
        String toEmail,
        String firstName,
        TicketResponse ticket,
        TicketMessageResponse message
    ) implements EmailEvent {}

    record CreditIssuedEmail(
        String toEmail,
        String firstName,
        long amountCents,
        String reason
    ) implements EmailEvent {}

    record ReplacementOrderEmail(
        String toEmail,
        String firstName,
        OrderResponse replacementOrder
    ) implements EmailEvent {}

    record BackInStockEmail(
        String toEmail,
        String firstName,
        long productId,
        String productName,
        Long variantId,
        String variantTitle,
        String productUrl
    ) implements EmailEvent {}

    record TeamInviteEmail(
        String toEmail,
        String companyName,
        String role,
        String inviterDisplayName,
        String acceptUrl
    ) implements EmailEvent {}
}
