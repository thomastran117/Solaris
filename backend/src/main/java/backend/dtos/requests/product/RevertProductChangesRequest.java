package backend.dtos.requests.product;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Reverts one or more {@code ProductChangeLog} entries by id. Each entry must belong to the
 * product (or one of its variants) on the URL. Stock-related entries are rejected — those
 * are managed via the inventory adjustment flow.
 */
@Getter
@Setter
public class RevertProductChangesRequest {

    @NotEmpty(message = "logEntryIds must not be empty")
    private List<@NotNull Long> logEntryIds;

    /**
     * Optimistic-lock guard: caller's expected {@code Product.version}. When non-null and the
     * server's version differs, the revert fails with 409 instead of clobbering concurrent edits.
     */
    private Long expectedVersion;
}
