package backend.services.impl.notification;

import backend.services.intf.notification.SmsService;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TwilioSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsService.class);

    private final String fromNumber;

    public TwilioSmsService(
            @Value("${app.twilio.account-sid}") String accountSid,
            @Value("${app.twilio.auth-token}") String authToken,
            @Value("${app.twilio.from-number}") String fromNumber) {
        this.fromNumber = fromNumber;
        if (accountSid != null && !accountSid.isBlank() && authToken != null && !authToken.isBlank()) {
            Twilio.init(accountSid, authToken);
        }
    }

    @Override
    public void sendSms(String toPhoneNumber, String body) {
        try {
            Message message = Message.creator(
                            new PhoneNumber(toPhoneNumber),
                            new PhoneNumber(fromNumber),
                            body)
                    .create();
            log.info("[SMS] Sent to={} sid={}", toPhoneNumber, message.getSid());
        } catch (Exception e) {
            throw new RuntimeException("Twilio SMS failed to=" + toPhoneNumber + ": " + e.getMessage(), e);
        }
    }
}
