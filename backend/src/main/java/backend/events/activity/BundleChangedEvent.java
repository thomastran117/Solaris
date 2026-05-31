package backend.events.activity;

import java.time.Instant;
import java.util.UUID;

public record BundleChangedEvent(
    UUID bundleId,
    ChangeType changeType,
    Instant occurredAt
) {}
