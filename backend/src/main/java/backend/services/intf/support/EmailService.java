package backend.services.intf.support;

import java.util.UUID;
import backend.dtos.responses.order.OrderResponse;
import backend.dtos.responses.support.TicketResponse;
import backend.dtos.responses.support.TicketMessageResponse;

public interface EmailService {

    /**
     * Sends a verification email to the given address containing a link with the token.
     * The send is executed asynchronously on a dedicated thread pool with exponential
     * backoff retries on transient SMTP failures.
     *
     * @param toEmail the recipient email address
     * @param token   the raw UUID verification token
     */
    void sendVerificationEmail(String toEmail, String token);

    /**
     * Sends a device verification email showing the browser, OS, and IP of the unrecognised
     * login attempt. The send is executed asynchronously with exponential backoff retries.
     *
     * @param toEmail  the recipient email address
     * @param token    the raw UUID device verification token
     * @param browser  browser name/version detected from the User-Agent
     * @param os       operating system detected from the User-Agent
     * @param ip       IP address of the login attempt
     */
    void sendDeviceVerificationEmail(String toEmail, String token, String browser, String os, String ip);

    /**
     * Sends an order receipt email to the customer after a successful order is placed.
     * The send is executed asynchronously with exponential backoff retries.
     *
     * @param toEmail   the customer's email address
     * @param firstName the customer's first name (used for personalised greeting; may be null)
     * @param order     the completed order response to render in the receipt
     */
    void sendOrderReceiptEmail(String toEmail, String firstName, OrderResponse order);

    /**
     * Sends a low-stock or out-of-stock alert email to the company owner.
     * The send is executed asynchronously with exponential backoff retries.
     *
     * @param toEmail      the owner's email address
     * @param firstName    the owner's first name (used for greeting; may be null)
     * @param productId    the product ID
     * @param productName  the product name
     * @param variantId    null for product-level stock; variant ID for variant-level
     * @param variantSku   null for product-level; variant SKU for variant-level
     * @param currentStock the stock level that triggered the alert
     * @param threshold    the quantity threshold that was breached (may be null if only percent threshold breached)
     * @param outOfStock   true if stock has reached zero
     */
    void sendLowStockAlertEmail(String toEmail, String firstName,
                                UUID productId, String productName,
                                UUID variantId, String variantSku,
                                int currentStock, Integer threshold,
                                boolean outOfStock);

    /**
     * Notifies the customer that their support ticket has been created.
     * Sent asynchronously with exponential backoff retries.
     */
    void sendTicketCreatedEmail(String toEmail, String firstName, TicketResponse ticket);

    /**
     * Notifies the customer (or staff, depending on the reply direction) that a new
     * message has been added to their ticket thread.
     * Sent asynchronously with exponential backoff retries.
     */
    void sendTicketReplyEmail(String toEmail, String firstName, TicketResponse ticket,
                              TicketMessageResponse message);

    /**
     * Notifies the customer that store credit has been issued to their account.
     * Sent asynchronously with exponential backoff retries.
     *
     * @param amountCents the credit amount in cents
     * @param reason      staff-supplied reason for the credit
     */
    void sendCreditIssuedEmail(String toEmail, String firstName, long amountCents, String reason);

    /**
     * Notifies the customer that a replacement order has been created for them.
     * Sent asynchronously with exponential backoff retries.
     */
    void sendReplacementOrderEmail(String toEmail, String firstName, OrderResponse replacementOrder);

    /**
     * Notifies a customer that a product (or specific variant) they subscribed to is back in stock.
     * Sent asynchronously with exponential backoff retries.
     *
     * @param toEmail      the customer's email address
     * @param firstName    the customer's first name (may be null)
     * @param productId    the product ID
     * @param productName  the product name
     * @param variantId    null for product-level; variant ID for variant-level
     * @param variantTitle null for product-level; human-readable variant label (e.g. "Black / 32GB")
     * @param productUrl   the frontend URL to the product page
     */
    void sendBackInStockEmail(String toEmail, String firstName,
                              UUID productId, String productName,
                              UUID variantId, String variantTitle,
                              String productUrl);

    /**
     * Notifies an invitee that they've been invited to join a company as a manager
     * or employee. The accept URL is a single-use link that activates the membership
     * when followed by the recipient.
     *
     * @param toEmail            invitee email address
     * @param companyName        company they've been invited to
     * @param role               human-readable role label ("Manager" / "Employee")
     * @param inviterDisplayName who sent the invite; may be null
     * @param acceptUrl          deep link to the frontend invite-accept page
     */
    void sendTeamInviteEmail(String toEmail, String companyName, String role,
                             String inviterDisplayName, String acceptUrl);

    void sendAbandonedCartEmail(String toEmail, String firstName,
                                UUID userId, UUID orderId,
                                java.util.List<backend.events.email.EmailEvent.AbandonedItem> items);

    void sendQuestionPostedEmail(UUID vendorUserId, String toEmail, String vendorFirstName,
                                 String productName, String questionText, UUID questionId);

    void sendGiftCardIssuedEmail(String toEmail, String firstName,
                                 String giftCardCode, int originalValueCents, String companyName);

    void sendPriceDropEmail(String toEmail, UUID userId, String productName,
                            String productUrl, int oldPriceCents, int newPriceCents);

    /**
     * Notifies the customer that the vendor cannot fulfil their requested delivery slot.
     * Sent asynchronously with exponential backoff retries.
     *
     * @param userId         the customer's user ID
     * @param recipientEmail the customer's email address
     * @param orderReference short, human-readable order reference (e.g. first 8 chars of the order ID)
     * @param requestedDate  the delivery date the customer had requested
     * @param vendorReason   optional vendor-supplied reason; may be null
     */
    void sendDeliverySlotUnavailableEmail(UUID userId, String recipientEmail, String orderReference,
                                          java.time.LocalDate requestedDate, String vendorReason);

    void sendMarketingWorkflowEmail(String toEmail, String firstName,
                                    UUID workflowId, UUID companyId,
                                    String subject, String body);

    void sendPurchaseOrderEmail(String supplierEmail, String supplierName,
                                String companyName, String poReference,
                                java.util.List<backend.events.email.EmailEvent.POLineItemSummary> items,
                                java.time.LocalDate expectedArrival);

    /** Notifies the vendor that a buyer has submitted a new B2B quote request. */
    void sendQuoteReceivedEmail(String vendorEmail, String vendorName, String buyerCompanyName,
                                UUID quoteId, long totalCents);

    /** Notifies the buyer that the vendor approved or countered their quote. */
    void sendQuoteRespondedEmail(String buyerEmail, String buyerName, String vendorName,
                                 UUID quoteId, String responseStatus);

    /** Notifies the buyer that a net-terms invoice has been issued; a PDF is attached. */
    void sendInvoiceIssuedEmail(String buyerEmail, String buyerName, String vendorName,
                                String invoiceNumber, long totalCents, java.time.LocalDate dueDate,
                                String currency,
                                java.util.List<backend.events.email.EmailEvent.InvoiceLineItem> items);

    /**
     * Sends a 6-digit one-time code to {@code toEmail} for MFA enrollment.
     * The email is dispatched asynchronously via Kafka.
     */
    void sendMfaOtpEmail(String toEmail, String code);

    /**
     * Alerts the support team that a new chargeback has been opened against a charge.
     * Dispatched asynchronously via Kafka after the enclosing transaction commits.
     *
     * @param recipientEmail   support team address ({@code app.email.support-team-email})
     * @param orderId          the disputed order; null when the charge could not be mapped to one
     * @param amountCents      disputed amount in the smallest currency unit
     * @param reason           raw Stripe dispute reason code
     * @param evidenceDeadline when evidence must be submitted by; may be null
     */
    void sendDisputeAlertEmail(String recipientEmail, String stripeDisputeId, UUID orderId,
                               long amountCents, String currency, String reason,
                               java.time.Instant evidenceDeadline);
}
