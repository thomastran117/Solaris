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
}
