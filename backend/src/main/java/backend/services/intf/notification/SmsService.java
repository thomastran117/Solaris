package backend.services.intf.notification;

public interface SmsService {
    void sendSms(String toPhoneNumber, String body);
}
