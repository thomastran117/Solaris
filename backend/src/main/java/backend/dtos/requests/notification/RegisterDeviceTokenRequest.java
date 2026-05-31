package backend.dtos.requests.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterDeviceTokenRequest(
    @NotBlank(message = "platform is required")
    @Pattern(regexp = "ANDROID|IOS|WEB", message = "platform must be ANDROID, IOS, or WEB")
    String platform,

    @NotBlank(message = "token is required")
    String token
) {}
