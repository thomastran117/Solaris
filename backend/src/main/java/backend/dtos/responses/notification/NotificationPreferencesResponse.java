package backend.dtos.responses.notification;

public record NotificationPreferencesResponse(
    boolean pushEnabled,
    boolean smsEnabled,
    String smsPhoneNumber
) {}
