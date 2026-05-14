package backend.repositories;

import backend.models.core.ImportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
    Optional<ImportJob> findByIdAndCompanyId(long id, long companyId);
    Page<ImportJob> findAllByCompanyIdOrderByCreatedAtDesc(long companyId, Pageable pageable);
}
