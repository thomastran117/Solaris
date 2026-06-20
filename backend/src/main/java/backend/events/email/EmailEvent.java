package backend.events.email;

import backend.dtos.responses.order.OrderResponse;
import backend.dtos.responses.support.TicketMessageResponse;
import backend.dtos.responses.support.TicketResponse;
import backend.models.enums.AnnouncementType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

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
    @JsonSubTypes.Type(value = EmailEvent.AnnouncementEmail.class,       name = "ANNOUNCEMENT"),
    @JsonSubTypes.Type(value = EmailEvent.AbandonedCartEmail.class,      name = "ABANDONED_CART"),
    @JsonSubTypes.Type(value = EmailEvent.QuestionPostedEmail.class,     name = "QUESTION_POSTED"),
    @JsonSubTypes.Type(value = EmailEvent.GiftCardIssuedEmail.class,    name = "GIFT_CARD_ISSUED"),
    @JsonSubTypes.Type(value = EmailEvent.PriceDropEmail.class,         name = "PRICE_DROP"),
    @JsonSubTypes.Type(value = EmailEvent.DeliverySlotUnavailableEmail.class, name = "DELIVERY_SLOT_UNAVAILABLE"),
    @JsonSubTypes.Type(value = EmailEvent.MarketingWorkflowEmail.class,       name = "MARKETING_WORKFLOW"),
    @JsonSubTypes.Type(value = EmailEvent.PurchaseOrderEmail.class,           name = "PURCHASE_ORDER"),
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
                EmailEvent.TeamInviteEmail,
                EmailEvent.AnnouncementEmail,
                EmailEvent.AbandonedCartEmail,
                EmailEvent.QuestionPostedEmail,
                EmailEvent.GiftCardIssuedEmail,
                EmailEvent.PriceDropEmail,
                EmailEvent.DeliverySlotUnavailableEmail,
                EmailEvent.MarketingWorkflowEmail,
                EmailEvent.PurchaseOrderEmail {

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
        java.util.UUID productId,
        String productName,
        java.util.UUID variantId,
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
        java.util.UUID productId,
        String productName,
        java.util.UUID variantId,
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

    record AnnouncementEmail(
        String toEmail,
        String firstName,
        java.util.UUID companyId,
        String companyName,
        java.util.UUID announcementId,
        String announcementTitle,
        String announcementBody,
        AnnouncementType announcementType
    ) implements EmailEvent {}

    record AbandonedItem(
        java.util.UUID productId,
        String name,
        String imageUrl,
        long priceCents
    ) {}

    record AbandonedCartEmail(
        String toEmail,
        String firstName,
        java.util.UUID userId,
        java.util.UUID orderId,
        List<AbandonedItem> items
    ) implements EmailEvent {}

    record QuestionPostedEmail(
        java.util.UUID vendorUserId,
        String toEmail,
        String vendorFirstName,
        String productName,
        String questionText,
        java.util.UUID questionId
    ) implements EmailEvent {}

    record GiftCardIssuedEmail(
        String toEmail,
        String firstName,
        String giftCardCode,
        int originalValueCents,
        String companyName
    ) implements EmailEvent {}

    record PriceDropEmail(
        java.util.UUID userId,
        String recipientEmail,
        String productName,
        String productUrl,
        int oldPriceCents,
        int newPriceCents
    ) implements EmailEvent {}

    record DeliverySlotUnavailableEmail(
        java.util.UUID userId,
        String recipientEmail,
        String orderReference,
        java.time.LocalDate requestedDate,
        String vendorReason
    ) implements EmailEvent {}

    record MarketingWorkflowEmail(
        String toEmail,
        String firstName,
        java.util.UUID workflowId,
        java.util.UUID companyId,
        String subject,
        String body
    ) implements EmailEvent {}

    record POLineItemSummary(
        String productName,
        String variantSku,
        int orderedQty,
        long unitCostCents
    ) {}

    record PurchaseOrderEmail(
        String supplierEmail,
        String supplierName,
        String companyName,
        String poReference,
        List<POLineItemSummary> items,
        java.time.LocalDate expectedArrival
    ) implements EmailEvent {}
}
