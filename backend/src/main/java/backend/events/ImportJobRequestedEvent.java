package backend.events;

import java.util.UUID;

/**
 * Spring application event published after an {@link backend.models.core.ImportJob}
 * is committed. Listened to by the Kafka publisher to relay the trigger to the
 * import worker (which can run on a separate node).
 */
public record ImportJobRequestedEvent(UUID jobId, UUID companyId, UUID uploadedBy) {}
