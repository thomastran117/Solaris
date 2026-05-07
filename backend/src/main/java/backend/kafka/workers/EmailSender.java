package backend.kafka.workers;

import backend.configurations.environment.EnvironmentSetting;
import backend.dtos.responses.order.OrderItemResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.dtos.responses.support.TicketMessageResponse;
import backend.dtos.responses.support.TicketResponse;
import backend.events.email.EmailEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class EmailSender {

    private final JavaMailSender mailSender;
    private final EnvironmentSetting env;
    private final RetryTemplate retryTemplate;

    public EmailSender(JavaMailSender mailSender,
                       EnvironmentSetting env,
                       @Qualifier("emailRetryTemplate") RetryTemplate retryTemplate) {
        this.mailSender = mailSender;
        this.env = env;
        this.retryTemplate = retryTemplate;
    }

    public void send(EmailEvent event) {
        switch (event) {
            case EmailEvent.VerificationEmail e ->
                sendVerificationEmail(e.toEmail(), e.token());
            case EmailEvent.DeviceVerificationEmail e ->
                sendDeviceVerificationEmail(e.toEmail(), e.token(), e.browser(), e.os(), e.ip());
            case EmailEvent.OrderReceiptEmail e ->
                sendOrderReceiptEmail(e.toEmail(), e.firstName(), e.order());
            case EmailEvent.LowStockAlertEmail e ->
                sendLowStockAlertEmail(e.toEmail(), e.firstName(), e.productId(), e.productName(),
                        e.variantId(), e.variantSku(), e.currentStock(), e.threshold(), e.outOfStock());
            case EmailEvent.TicketCreatedEmail e ->
                sendTicketCreatedEmail(e.toEmail(), e.firstName(), e.ticket());
            case EmailEvent.TicketReplyEmail e ->
                sendTicketReplyEmail(e.toEmail(), e.firstName(), e.ticket(), e.message());
            case EmailEvent.CreditIssuedEmail e ->
                sendCreditIssuedEmail(e.toEmail(), e.firstName(), e.amountCents(), e.reason());
            case EmailEvent.ReplacementOrderEmail e ->
                sendReplacementOrderEmail(e.toEmail(), e.firstName(), e.replacementOrder());
            case EmailEvent.BackInStockEmail e ->
                sendBackInStockEmail(e.toEmail(), e.firstName(), e.productId(), e.productName(),
                        e.variantId(), e.variantTitle(), e.productUrl());
        }
    }

    // ─── Shared HTML shell ────────────────────────────────────────────────────

    private String wrapInShell(String headerLabel, String bodyHtml) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin:0;padding:0;background-color:#EFF6FF;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" role="presentation">
                <tr>
                  <td align="center" style="padding:48px 16px;">

                    <!-- Card -->
                    <table width="580" cellpadding="0" cellspacing="0" role="presentation"
                           style="background:#ffffff;border-radius:12px;overflow:hidden;
                                  box-shadow:0 4px 24px rgba(30,64,175,0.10);">

                      <!-- Top accent bar -->
                      <tr>
                        <td style="background:linear-gradient(90deg,#1D4ED8 0%%,#3B82F6 100%%);
                                   height:5px;font-size:0;line-height:0;">&nbsp;</td>
                      </tr>

                      <!-- Header -->
                      <tr>
                        <td style="padding:32px 40px 0 40px;">
                          <table width="100%%" cellpadding="0" cellspacing="0" role="presentation">
                            <tr>
                              <td>
                                <span style="font-size:20px;font-weight:800;letter-spacing:-0.5px;color:#1D4ED8;">
                                  ShopWave
                                </span>
                              </td>
                              <td align="right">
                                <span style="font-size:11px;font-weight:600;letter-spacing:0.08em;
                                             text-transform:uppercase;color:#93C5FD;">
                                  %s
                                </span>
                              </td>
                            </tr>
                          </table>
                          <hr style="border:none;border-top:1px solid #DBEAFE;margin:20px 0 0 0;">
                        </td>
                      </tr>

                      <!-- Body -->
                      <tr>
                        <td style="padding:28px 40px 36px 40px;">
                          %s
                        </td>
                      </tr>

                      <!-- Footer -->
                      <tr>
                        <td style="background:#F8FAFF;border-top:1px solid #DBEAFE;
                                   padding:18px 40px;border-radius:0 0 12px 12px;">
                          <p style="margin:0;font-size:11px;color:#94A3B8;line-height:1.6;">
                            ShopWave &mdash; 123 Commerce Street, San Francisco, CA 94105<br>
                            You are receiving this email because of activity on your account.
                          </p>
                        </td>
                      </tr>

                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(headerLabel, bodyHtml);
    }

    // ─── Button helper ────────────────────────────────────────────────────────

    private String primaryButton(String href, String label) {
        return """
            <a href="%s"
               style="display:inline-block;margin-top:28px;
                      background:linear-gradient(135deg,#1D4ED8 0%%,#3B82F6 100%%);
                      color:#ffffff;padding:14px 36px;border-radius:8px;
                      text-decoration:none;font-size:15px;font-weight:700;
                      letter-spacing:0.02em;box-shadow:0 4px 14px rgba(59,130,246,0.40);">
              %s
            </a>
            """.formatted(href, label);
    }

    // ─── Expiry note helper ───────────────────────────────────────────────────

    private String expiryNote(String text) {
        return """
            <p style="margin:20px 0 0 0;font-size:12px;color:#94A3B8;line-height:1.6;">
              %s
            </p>
            """.formatted(text);
    }

    // ─── Send methods ─────────────────────────────────────────────────────────

    private void sendVerificationEmail(String toEmail, String token) {
        String verifyUrl = env.getEmail().getVerificationBaseUrl() + "/verify-email?token=" + token;
        String htmlBody = buildVerificationHtml(toEmail, verifyUrl);
        sendMimeMessage(toEmail, "Verify your ShopWave account", htmlBody);
    }

    private void sendDeviceVerificationEmail(String toEmail, String token,
                                             String browser, String os, String ip) {
        String verifyUrl = env.getEmail().getVerificationBaseUrl() + "/verify-device?token=" + token;
        String htmlBody = buildDeviceVerificationHtml(toEmail, verifyUrl, browser, os, ip);
        sendMimeMessage(toEmail, "Verify new device — ShopWave", htmlBody);
    }

    private void sendOrderReceiptEmail(String toEmail, String firstName, OrderResponse order) {
        String htmlBody = buildOrderReceiptHtml(toEmail, firstName, order);
        sendMimeMessage(toEmail, "Your ShopWave order #" + order.getId() + " — Receipt", htmlBody);
    }

    private void sendTicketCreatedEmail(String toEmail, String firstName, TicketResponse ticket) {
        String greeting = firstName != null ? "Hi " + firstName + "," : "Hi,";
        String body = """
            <h1 style="margin:0 0 8px 0;font-size:26px;font-weight:800;color:#0F172A;letter-spacing:-0.5px;">
              Your support ticket has been received
            </h1>
            <p style="margin:0 0 16px 0;font-size:15px;color:#475569;line-height:1.7;">%s</p>
            <p style="margin:0 0 16px 0;font-size:15px;color:#475569;line-height:1.7;">
              Ticket <strong>#%d</strong> — <em>%s</em><br>
              Our team will review your request and respond shortly.
            </p>
            """.formatted(greeting, ticket.getId(), ticket.getSubject());
        sendMimeMessage(toEmail, "Support ticket #" + ticket.getId() + " received — ShopWave",
                wrapInShell("Support", body));
    }

    private void sendTicketReplyEmail(String toEmail, String firstName, TicketResponse ticket,
                                      TicketMessageResponse message) {
        String greeting = firstName != null ? "Hi " + firstName + "," : "Hi,";
        String body = """
            <h1 style="margin:0 0 8px 0;font-size:26px;font-weight:800;color:#0F172A;letter-spacing:-0.5px;">
              New reply on your support ticket
            </h1>
            <p style="margin:0 0 16px 0;font-size:15px;color:#475569;line-height:1.7;">%s</p>
            <p style="margin:0 0 8px 0;font-size:14px;color:#64748B;">
              Ticket <strong>#%d</strong> — %s
            </p>
            <div style="background:#F8FAFF;border-left:4px solid #3B82F6;border-radius:4px;padding:16px 20px;margin:16px 0;">
              <p style="margin:0;font-size:14px;color:#334155;line-height:1.7;">%s</p>
            </div>
            """.formatted(greeting, ticket.getId(), ticket.getSubject(), message.getBody());
        sendMimeMessage(toEmail, "New reply on ticket #" + ticket.getId() + " — ShopWave",
                wrapInShell("Support", body));
    }

    private void sendCreditIssuedEmail(String toEmail, String firstName, long amountCents, String reason) {
        String greeting = firstName != null ? "Hi " + firstName + "," : "Hi,";
        String formatted = String.format("$%.2f", amountCents / 100.0);
        String reasonLine = reason != null && !reason.isBlank()
                ? "<p style=\"margin:8px 0 0 0;font-size:13px;color:#64748B;\">Reason: " + reason + "</p>"
                : "";
        String body = """
            <h1 style="margin:0 0 8px 0;font-size:26px;font-weight:800;color:#0F172A;letter-spacing:-0.5px;">
              You've received store credit!
            </h1>
            <p style="margin:0 0 16px 0;font-size:15px;color:#475569;line-height:1.7;">%s</p>
            <div style="background:#EFF6FF;border-radius:8px;padding:20px;margin:16px 0;text-align:center;">
              <p style="margin:0;font-size:32px;font-weight:800;color:#1D4ED8;">%s</p>
              <p style="margin:4px 0 0 0;font-size:13px;color:#64748B;">store credit added to your account</p>
              %s
            </div>
            <p style="margin:16px 0 0 0;font-size:14px;color:#475569;line-height:1.7;">
              This credit will be automatically applied to your next qualifying order.
            </p>
            """.formatted(greeting, formatted, reasonLine);
        sendMimeMessage(toEmail, "You've received " + formatted + " in store credit — ShopWave",
                wrapInShell("Store Credit", body));
    }

    private void sendBackInStockEmail(String toEmail, String firstName,
                                      long productId, String productName,
                                      Long variantId, String variantTitle,
                                      String productUrl) {
        String greeting = firstName != null ? "Hi " + firstName + "," : "Hi,";
        String itemLine = variantTitle != null
                ? productName + " &mdash; <strong>" + variantTitle + "</strong>"
                : "<strong>" + productName + "</strong>";
        String body = """
            <h1 style="margin:0 0 8px 0;font-size:26px;font-weight:800;color:#0F172A;letter-spacing:-0.5px;">
              Good news — it's back in stock!
            </h1>
            <p style="margin:0 0 16px 0;font-size:15px;color:#475569;line-height:1.7;">%s</p>
            <div style="background:#EFF6FF;border:1px solid #BFDBFE;border-radius:10px;padding:20px;margin:16px 0;">
              <p style="margin:0;font-size:13px;font-weight:700;letter-spacing:0.06em;
                         text-transform:uppercase;color:#93C5FD;">Product</p>
              <p style="margin:6px 0 0 0;font-size:16px;color:#0F172A;font-weight:600;line-height:1.4;">%s</p>
              <span style="display:inline-block;margin-top:10px;padding:4px 12px;
                           background:#22C55E;border-radius:20px;font-size:12px;
                           font-weight:700;color:#ffffff;letter-spacing:0.04em;">
                In Stock
              </span>
            </div>
            <p style="margin:16px 0 0 0;font-size:14px;color:#475569;line-height:1.7;">
              Grab it before it sells out again — stock is limited.
            </p>
            %s
            """.formatted(greeting, itemLine, primaryButton(productUrl, "Shop Now"));
        sendMimeMessage(toEmail, "Back in stock: " + productName + " — ShopWave",
                wrapInShell("Back in Stock", body));
    }

    private void sendReplacementOrderEmail(String toEmail, String firstName, OrderResponse replacementOrder) {
        String greeting = firstName != null ? "Hi " + firstName + "," : "Hi,";
        String body = """
            <h1 style="margin:0 0 8px 0;font-size:26px;font-weight:800;color:#0F172A;letter-spacing:-0.5px;">
              Your replacement order is on its way
            </h1>
            <p style="margin:0 0 16px 0;font-size:15px;color:#475569;line-height:1.7;">%s</p>
            <p style="margin:0 0 16px 0;font-size:15px;color:#475569;line-height:1.7;">
              We've created replacement order <strong>#%d</strong> for you at no charge.
              You'll receive a shipping confirmation once it has been dispatched.
            </p>
            """.formatted(greeting, replacementOrder.getId());
        sendMimeMessage(toEmail, "Your replacement order #" + replacementOrder.getId() + " — ShopWave",
                wrapInShell("Replacement Order", body));
    }

    private void sendLowStockAlertEmail(String toEmail, String firstName,
                                        long productId, String productName,
                                        Long variantId, String variantSku,
                                        int currentStock, Integer threshold,
                                        boolean outOfStock) {
        String htmlBody = buildLowStockAlertHtml(firstName, productId, productName,
                variantId, variantSku, currentStock, threshold, outOfStock);
        String subject = outOfStock
                ? "Out of stock: " + productName + " — ShopWave"
                : "Low stock alert: " + productName + " — ShopWave";
        retryTemplate.execute(context -> {
            MimeMessage message = mailSender.createMimeMessage();
            try {
                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                helper.setFrom(env.getEmail().getFrom());
                helper.setTo(toEmail);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
            mailSender.send(message);
            return null;
        });
    }

    private void sendMimeMessage(String toEmail, String subject, String htmlBody) {
        retryTemplate.execute(context -> {
            MimeMessage message = mailSender.createMimeMessage();
            try {
                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                helper.setFrom(env.getEmail().getFrom());
                helper.setTo(toEmail);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
            mailSender.send(message);
            return null;
        });
    }

    // ─── Email templates ──────────────────────────────────────────────────────

    private String buildVerificationHtml(String email, String verifyUrl) {
        String body = """
            <h1 style="margin:0 0 8px 0;font-size:26px;font-weight:800;
                        color:#0F172A;letter-spacing:-0.5px;">
              Verify your email address
            </h1>
            <p style="margin:0 0 4px 0;font-size:15px;color:#475569;line-height:1.7;">
              Welcome to ShopWave! You signed up with <strong style="color:#1D4ED8;">%s</strong>.
            </p>
            <p style="margin:0;font-size:15px;color:#475569;line-height:1.7;">
              Click the button below to activate your account. This link is valid for 24&nbsp;hours.
            </p>
            %s
            %s
            """.formatted(
                email,
                primaryButton(verifyUrl, "Verify Email Address"),
                expiryNote("If you did not create an account, you can safely ignore this email.")
            );
        return wrapInShell("Account Activation", body);
    }

    private String buildDeviceVerificationHtml(String email, String verifyUrl,
                                               String browser, String os, String ip) {
        String body = """
            <!-- Alert strip -->
            <table width="100%%" cellpadding="0" cellspacing="0" role="presentation"
                   style="background:#EFF6FF;border:1px solid #BFDBFE;border-radius:8px;margin-bottom:24px;">
              <tr>
                <td style="padding:14px 18px;">
                  <p style="margin:0;font-size:13px;font-weight:700;color:#1D4ED8;letter-spacing:0.02em;">
                    &#x26A0;&#xFE0F;&nbsp; New device detected
                  </p>
                  <p style="margin:4px 0 0 0;font-size:13px;color:#3B82F6;">
                    A login was attempted on <strong>%s</strong> from an unrecognised device.
                  </p>
                </td>
              </tr>
            </table>

            <h1 style="margin:0 0 8px 0;font-size:24px;font-weight:800;
                        color:#0F172A;letter-spacing:-0.5px;">
              New Device Login Attempt
            </h1>
            <p style="margin:0 0 20px 0;font-size:15px;color:#475569;line-height:1.7;">
              If this was you, verify the device below to continue. If not, change your password immediately.
            </p>

            <!-- Device detail table -->
            <table width="100%%" cellpadding="0" cellspacing="0" role="presentation"
                   style="border-collapse:collapse;border-radius:8px;overflow:hidden;
                          border:1px solid #DBEAFE;">
              <tr style="background:#F0F7FF;">
                <td style="padding:10px 16px;font-size:12px;font-weight:700;letter-spacing:0.06em;
                           text-transform:uppercase;color:#64748B;width:140px;">Browser</td>
                <td style="padding:10px 16px;font-size:14px;color:#0F172A;font-weight:500;">%s</td>
              </tr>
              <tr style="background:#ffffff;border-top:1px solid #DBEAFE;">
                <td style="padding:10px 16px;font-size:12px;font-weight:700;letter-spacing:0.06em;
                           text-transform:uppercase;color:#64748B;">OS</td>
                <td style="padding:10px 16px;font-size:14px;color:#0F172A;font-weight:500;">%s</td>
              </tr>
              <tr style="background:#F0F7FF;border-top:1px solid #DBEAFE;">
                <td style="padding:10px 16px;font-size:12px;font-weight:700;letter-spacing:0.06em;
                           text-transform:uppercase;color:#64748B;">IP Address</td>
                <td style="padding:10px 16px;font-size:14px;color:#0F172A;font-weight:500;">%s</td>
              </tr>
            </table>

            %s
            %s
            """.formatted(
                email,
                browser, os, ip,
                primaryButton(verifyUrl, "Verify Device"),
                expiryNote("This link expires in <strong>10 minutes</strong>. "
                         + "If you did not attempt to log in, please change your password immediately.")
            );
        return wrapInShell("Security Alert", body);
    }

    private String buildLowStockAlertHtml(String firstName,
                                          long productId, String productName,
                                          Long variantId, String variantSku,
                                          int currentStock, Integer threshold,
                                          boolean outOfStock) {
        String greeting = (firstName != null && !firstName.isBlank()) ? firstName : "there";
        String badgeColor = outOfStock ? "#dc2626" : "#d97706";
        String badgeText = outOfStock ? "Out of Stock" : "Low Stock";
        String itemLine = variantSku != null
                ? productName + " &mdash; <span style=\"color:#555555;\">SKU: " + variantSku + "</span>"
                : productName;
        String stockLine = outOfStock
                ? "<strong style=\"color:#dc2626;\">0 units remaining</strong>"
                : "<strong style=\"color:#d97706;\">" + currentStock + " unit" + (currentStock == 1 ? "" : "s") + " remaining</strong>"
                  + (threshold != null ? " (threshold: " + threshold + ")" : "");

        return """
            <!DOCTYPE html>
            <html lang="en">
            <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 0;">
              <table width="100%%" cellpadding="0" cellspacing="0">
                <tr>
                  <td align="center" style="padding: 40px 0;">
                    <table width="600" cellpadding="0" cellspacing="0"
                           style="background-color: #ffffff; border-radius: 8px;
                                  box-shadow: 0 2px 8px rgba(0,0,0,0.08); padding: 40px;">
                      <tr>
                        <td>
                          <span style="display:inline-block; background-color:%s; color:#ffffff;
                                       font-size:12px; font-weight:bold; padding:4px 10px;
                                       border-radius:4px; letter-spacing:.05em; text-transform:uppercase;">
                            %s
                          </span>
                          <h1 style="color:#333333; font-size:22px; margin:16px 0 4px;">
                            Inventory Alert
                          </h1>
                          <p style="color:#666666; font-size:15px; line-height:1.5; margin-top:0;">
                            Hi <strong>%s</strong>, one of your products needs attention.
                          </p>
                          <table style="width:100%%; border-collapse:collapse; margin:20px 0;
                                        background-color:#f9f9f9; border-radius:6px; padding:16px;">
                            <tr>
                              <td style="padding:10px 16px; color:#999999; font-size:13px; font-weight:bold; width:130px;">Product</td>
                              <td style="padding:10px 16px; color:#333333; font-size:14px;">%s</td>
                            </tr>
                            <tr>
                              <td style="padding:10px 16px; color:#999999; font-size:13px; font-weight:bold;">Product ID</td>
                              <td style="padding:10px 16px; color:#333333; font-size:14px;">%d</td>
                            </tr>
                            <tr>
                              <td style="padding:10px 16px; color:#999999; font-size:13px; font-weight:bold;">Stock</td>
                              <td style="padding:10px 16px; font-size:14px;">%s</td>
                            </tr>
                          </table>
                          <p style="color:#666666; font-size:14px; line-height:1.5;">
                            Please restock this product or review your inventory settings to
                            avoid order fulfilment issues.
                          </p>
                          <hr style="border:none; border-top:1px solid #eeeeee; margin:24px 0;">
                          <p style="color:#cccccc; font-size:12px; margin:0;">
                            ShopWave &mdash; You are receiving this because a low-stock threshold was reached.
                          </p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(badgeColor, badgeText, greeting, itemLine, productId, stockLine);
    }

    private String buildOrderReceiptHtml(String email, String firstName, OrderResponse order) {
        String greeting = (firstName != null && !firstName.isBlank()) ? firstName : email;
        String dateStr = order.getCreatedAt() != null
                ? DateTimeFormatter.ofPattern("MMMM d, yyyy")
                        .withZone(ZoneId.of("UTC"))
                        .format(order.getCreatedAt())
                : "—";
        String currency = order.getCurrency() != null ? order.getCurrency().toUpperCase() : "USD";

        StringBuilder itemRows = new StringBuilder();
        for (OrderItemResponse item : order.getItems()) {
            String name = item.getBundleName() != null ? item.getBundleName() : item.getProductName();
            String variant = item.getVariantTitle() != null
                    ? "<br><span style=\"font-size:12px;color:#94A3B8;\">" + item.getVariantTitle() + "</span>"
                    : "";
            BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            itemRows.append("""
                <tr>
                  <td style="padding:12px 0;border-bottom:1px solid #EFF6FF;
                             font-size:14px;color:#0F172A;font-weight:500;line-height:1.4;">
                    %s%s
                  </td>
                  <td style="padding:12px 0;border-bottom:1px solid #EFF6FF;
                             font-size:14px;color:#64748B;text-align:center;">%d</td>
                  <td style="padding:12px 0;border-bottom:1px solid #EFF6FF;
                             font-size:14px;color:#0F172A;font-weight:600;text-align:right;">
                    %s&nbsp;%.2f
                  </td>
                </tr>
                """.formatted(name, variant, item.getQuantity(), currency, lineTotal));
        }

        String body = """
            <!-- Order confirmed banner -->
            <table width="100%%" cellpadding="0" cellspacing="0" role="presentation"
                   style="background:linear-gradient(135deg,#EFF6FF 0%%,#DBEAFE 100%%);
                          border:1px solid #BFDBFE;border-radius:10px;margin-bottom:24px;">
              <tr>
                <td style="padding:18px 20px;">
                  <p style="margin:0;font-size:20px;">&#x2705;</p>
                  <p style="margin:4px 0 0 0;font-size:15px;font-weight:700;color:#1D4ED8;">
                    Order Confirmed
                  </p>
                  <p style="margin:2px 0 0 0;font-size:13px;color:#3B82F6;">
                    Hi <strong>%s</strong>, your order has been received and is being processed.
                  </p>
                </td>
              </tr>
            </table>

            <!-- Order meta -->
            <table width="100%%" cellpadding="0" cellspacing="0" role="presentation"
                   style="margin-bottom:24px;">
              <tr>
                <td style="font-size:12px;font-weight:700;letter-spacing:0.06em;
                           text-transform:uppercase;color:#94A3B8;padding-bottom:4px;">Order</td>
                <td style="font-size:14px;color:#0F172A;font-weight:600;
                           text-align:right;padding-bottom:4px;">#%d</td>
              </tr>
              <tr>
                <td style="font-size:12px;font-weight:700;letter-spacing:0.06em;
                           text-transform:uppercase;color:#94A3B8;padding-bottom:4px;">Date</td>
                <td style="font-size:14px;color:#0F172A;text-align:right;padding-bottom:4px;">%s</td>
              </tr>
              <tr>
                <td style="font-size:12px;font-weight:700;letter-spacing:0.06em;
                           text-transform:uppercase;color:#94A3B8;">Status</td>
                <td style="text-align:right;">
                  <span style="display:inline-block;padding:3px 10px;border-radius:20px;
                               background:#DBEAFE;color:#1D4ED8;font-size:12px;font-weight:700;">
                    %s
                  </span>
                </td>
              </tr>
            </table>

            <hr style="border:none;border-top:2px solid #EFF6FF;margin:0 0 20px 0;">

            <!-- Items table -->
            <table width="100%%" cellpadding="0" cellspacing="0" role="presentation">
              <thead>
                <tr>
                  <th style="font-size:11px;font-weight:700;letter-spacing:0.08em;
                             text-transform:uppercase;color:#94A3B8;text-align:left;
                             padding-bottom:10px;">Item</th>
                  <th style="font-size:11px;font-weight:700;letter-spacing:0.08em;
                             text-transform:uppercase;color:#94A3B8;text-align:center;
                             padding-bottom:10px;">Qty</th>
                  <th style="font-size:11px;font-weight:700;letter-spacing:0.08em;
                             text-transform:uppercase;color:#94A3B8;text-align:right;
                             padding-bottom:10px;">Total</th>
                </tr>
              </thead>
              <tbody>
                %s
              </tbody>
            </table>

            <!-- Order total -->
            <table width="100%%" cellpadding="0" cellspacing="0" role="presentation"
                   style="margin-top:16px;background:#EFF6FF;border-radius:8px;">
              <tr>
                <td style="padding:14px 16px;font-size:15px;font-weight:800;color:#0F172A;">
                  Order Total
                </td>
                <td style="padding:14px 16px;font-size:18px;font-weight:800;
                           color:#1D4ED8;text-align:right;">
                  %s&nbsp;%.2f
                </td>
              </tr>
            </table>
            """.formatted(
                greeting,
                order.getId(), dateStr, order.getStatus(),
                itemRows.toString(),
                currency, order.getTotalAmount()
            );

        return wrapInShell("Order Receipt", body);
    }
}
