package backend.events.imports;

/**
 * Kafka wire payload for triggering an import job. Kept in its own package so the
 * Kafka type-mapping trusted-packages config can scope JSON deserialization narrowly.
 */
public record ImportJobMessage(long jobId, long companyId, long uploadedBy) {}
