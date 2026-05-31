package backend.dtos.responses.device;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class DeviceResponse {
    private UUID id;
    private String fingerprint;
    private String deviceType;
    private String browser;
    private String os;
    private String lastIp;
    private Instant createdAt;
    private Instant lastSeenAt;
}
