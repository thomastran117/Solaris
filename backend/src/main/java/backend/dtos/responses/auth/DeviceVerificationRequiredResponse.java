package backend.dtos.responses.auth;

public record DeviceVerificationRequiredResponse(
        String status,
        String message
) {
    public static DeviceVerificationRequiredResponse standard() {
        return new DeviceVerificationRequiredResponse(
                "DEVICE_VERIFICATION_REQUIRED",
                "A verification email has been sent to your inbox. Please verify this device to continue."
        );
    }
}
