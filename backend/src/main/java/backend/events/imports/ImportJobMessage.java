package backend.events.imports;

import java.util.UUID;

/**
 * Kafka wire payload for triggering an import job. Kept in its own package so the
 * Kafka type-mapping trusted-packages config can scope JSON deserialization narrowly.
 */
public record ImportJobMessage(UUID jobId, UUID companyId, UUID uploadedBy) {}
