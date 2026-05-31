package backend.repositories.projections;

import backend.models.enums.CancellationReason;

/** Count of cancelled orders bucketed by the {@link CancellationReason} enum. */
public interface CancellationReasonCountProjection {
    CancellationReason getReason();
    Long getCount();
}
