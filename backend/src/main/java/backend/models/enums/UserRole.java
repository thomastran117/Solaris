package backend.models.enums;

/**
 * Authority/role for authorization (e.g. Spring Security).
 */
public enum UserRole {
    USER,
    MERCHANT,
    ADMIN,
    MODERATOR,
    SUPPORT,
    VENDOR_OWNER,
    VENDOR_STAFF,
    MARKETPLACE_OPERATOR
}
