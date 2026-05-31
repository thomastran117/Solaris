package backend.models.enums;

public enum ImportJobStatus {
    PENDING,
    PARSING,
    VALIDATING,
    PROCESSING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPLETED_WITH_ERRORS || this == FAILED;
    }
}
