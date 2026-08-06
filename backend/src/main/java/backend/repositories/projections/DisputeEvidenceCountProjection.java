package backend.repositories.projections;

import java.util.UUID;

/** Evidence entries per dispute case — used to avoid an N+1 count on the dispute list. */
public interface DisputeEvidenceCountProjection {
    UUID getDisputeCaseId();
    Long getCount();
}
