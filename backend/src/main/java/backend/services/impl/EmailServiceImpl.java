package backend.services.impl;

import backend.configurations.environment.EnvironmentSetting;
import backend.services.intf.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EnvironmentSetting env;
    private final RetryTemplate retryTemplate;

    public EmailServiceImpl(JavaMailSender mailSender,
                            EnvironmentSetting env,
                            @Qualifier("emailRetryTemplate") RetryTemplate retryTemplate) {
        this.mailSender = mailSender;
        this.env = env;
        this.retryTemplate = retryTemplate;
    }

    @Async("emailExecutor")
    @Override
    public void sendVerificationEmail(String toEmail, String token) {
        String verifyUrl = env.getEmail().getVerificationBaseUrl() + "/verify-email?token=" + token;
        String htmlBody = buildHtmlBody(toEmail, verifyUrl);

        retryTemplate.execute(context -> {
            MimeMessage message = mailSender.createMimeMessage();
            try {
                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                helper.setFrom(env.getEmail().getFrom());
                helper.setTo(toEmail);
                helper.setSubject("Verify your ShopWave account");
                helper.setText(htmlBody, true);
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
            mailSender.send(message);
            return null;
        });
    }

    @Async("emailExecutor")
    @Override
    public void sendDeviceVerificationEmail(String toEmail, String token,
                                            String browser, String os, String ip) {
        String verifyUrl = env.getEmail().getVerificationBaseUrl() + "/verify-device?token=" + token;
        String htmlBody = buildDeviceVerificationHtml(toEmail, verifyUrl, browser, os, ip);

        retryTemplate.execute(context -> {
            MimeMessage message = mailSender.createMimeMessage();
            try {
                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                helper.setFrom(env.getEmail().getFrom());
                helper.setTo(toEmail);
                helper.setSubject("Verify new device \u2014 ShopWave");
                helper.setText(htmlBody, true);
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
            mailSender.send(message);
            return null;
        });
    }

    private String buildDeviceVerificationHtml(String email, String verifyUrl,
                                               String browser, String os, String ip) {
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
                          <h1 style="color: #333333; font-size: 24px; margin-bottom: 8px;">
                            New Device Login Attempt
                          </h1>
                          <p style="color: #666666; font-size: 16px; line-height: 1.5;">
                            A login to your ShopWave account (<strong>%s</strong>) was attempted
                            from an unrecognised device.
                          </p>
                          <table style="width: 100%%; border-collapse: collapse; margin: 20px 0;">
                            <tr>
                              <td style="padding: 8px; border: 1px solid #eeeeee; color: #555555; font-weight: bold;">Browser</td>
                              <td style="padding: 8px; border: 1px solid #eeeeee; color: #333333;">%s</td>
                            </tr>
                            <tr>
                              <td style="padding: 8px; border: 1px solid #eeeeee; color: #555555; font-weight: bold;">Operating System</td>
                              <td style="padding: 8px; border: 1px solid #eeeeee; color: #333333;">%s</td>
                            </tr>
                            <tr>
                              <td style="padding: 8px; border: 1px solid #eeeeee; color: #555555; font-weight: bold;">IP Address</td>
                              <td style="padding: 8px; border: 1px solid #eeeeee; color: #333333;">%s</td>
                            </tr>
                          </table>
                          <a href="%s"
                             style="display: inline-block; margin: 24px 0;
                                    background-color: #4f46e5; color: #ffffff;
                                    padding: 14px 32px; border-radius: 6px;
                                    text-decoration: none; font-size: 16px; font-weight: bold;">
                            Verify Device
                          </a>
                          <p style="color: #999999; font-size: 13px; margin-top: 16px;">
                            This link expires in 10 minutes. If you did not attempt to log in,
                            please change your password immediately.
                          </p>
                          <hr style="border: none; border-top: 1px solid #eeeeee; margin: 24px 0;">
                          <p style="color: #cccccc; font-size: 12px;">
                            ShopWave &mdash; You are receiving this because a login was attempted on your account.
                          </p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(email, browser, os, ip, verifyUrl);
    }

    private String buildHtmlBody(String email, String verifyUrl) {
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
                          <h1 style="color: #333333; font-size: 24px; margin-bottom: 8px;">
                            Welcome to ShopWave
                          </h1>
                          <p style="color: #666666; font-size: 16px; line-height: 1.5;">
                            Thanks for signing up with <strong>%s</strong>.<br>
                            Please verify your email address to activate your account.
                          </p>
                          <a href="%s"
                             style="display: inline-block; margin: 24px 0;
                                    background-color: #4f46e5; color: #ffffff;
                                    padding: 14px 32px; border-radius: 6px;
                                    text-decoration: none; font-size: 16px; font-weight: bold;">
                            Verify Email
                          </a>
                          <p style="color: #999999; font-size: 13px; margin-top: 16px;">
                            This link expires in 24 hours. If you did not create an account,
                            you can safely ignore this email.
                          </p>
                          <hr style="border: none; border-top: 1px solid #eeeeee; margin: 24px 0;">
                          <p style="color: #cccccc; font-size: 12px;">
                            ShopWave &mdash; You are receiving this because you registered at shopwave.com.
                          </p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(email, verifyUrl);
    }
}
