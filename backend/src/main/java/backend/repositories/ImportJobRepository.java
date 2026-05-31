package backend.repositories;

import backend.models.core.ImportJob;
import backend.models.enums.ImportJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, java.util.UUID> {
    Optional<ImportJob> findByIdAndCompanyId(java.util.UUID id, java.util.UUID companyId);
    Page<ImportJob> findAllByCompanyIdOrderByCreatedAtDesc(java.util.UUID companyId, Pageable pageable);

    /**
     * Atomically claims a job for processing. Returns 1 if the calling worker is the
     * first to flip the status from PENDING to PARSING, 0 otherwise. Callers that
     * receive 0 must abandon the job — another consumer has it (or it already finished).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ImportJob j SET j.status = :to WHERE j.id = :id AND j.status = :from")
    int claimForProcessing(@Param("id") java.util.UUID id,
                           @Param("from") ImportJobStatus from,
                           @Param("to") ImportJobStatus to);
}
