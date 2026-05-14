package backend.events;

/**
 * Spring application event published after an {@link backend.models.core.ImportJob}
 * is committed. Listened to by the Kafka publisher to relay the trigger to the
 * import worker (which can run on a separate node).
 */
public record ImportJobRequestedEvent(long jobId, long companyId, long uploadedBy) {}
