package backend.dtos.requests.notification;

import jakarta.validation.constraints.Size;

public record UpdateNotificationPreferencesRequest(
    Boolean pushEnabled,
    Boolean smsEnabled,
    @Size(max = 30, message = "phone number must be at most 30 characters")
    String smsPhoneNumber
) {}
