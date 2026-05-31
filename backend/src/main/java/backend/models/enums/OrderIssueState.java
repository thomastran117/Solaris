package backend.models.enums;

public enum OrderIssueState {
    REPORTED,
    INVESTIGATING,
    AWAITING_CUSTOMER,
    RESOLVED_REFUND,
    RESOLVED_REPLACEMENT,
    RESOLVED_CREDIT,
    REJECTED,
    CANCELLED
}
