package backend.models.enums;

/**
 * Origin of a {@code ProductChangeLog} entry.
 *
 * <ul>
 *   <li>{@link #USER} — a person edited the product via the admin UI / API.</li>
 *   <li>{@link #SYSTEM} — an automated process (scheduler, worker, import) made the change.</li>
 *   <li>{@link #REVERT} — the change was produced by reverting a previous log entry.</li>
 * </ul>
 */
public enum ChangeSource {
    USER,
    SYSTEM,
    REVERT
}
