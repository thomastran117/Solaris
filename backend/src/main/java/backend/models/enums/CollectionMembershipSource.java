package backend.models.enums;

/**
 * Records whether a CollectionProduct row was added by an admin click (MANUAL) or
 * materialised from a DYNAMIC collection's rules ({@code AUTO}). The worker only
 * deletes/replaces rows it owns — MANUAL membership in a STATIC collection is never
 * touched by the rule evaluator.
 */
public enum CollectionMembershipSource {
    MANUAL,
    AUTO
}
