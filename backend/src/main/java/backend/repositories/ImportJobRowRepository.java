package backend.repositories;

import backend.models.core.ImportJobRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImportJobRowRepository extends JpaRepository<ImportJobRow, java.util.UUID> {
    Page<ImportJobRow> findAllByJobIdOrderByRowNumberAsc(UUID jobId, Pageable pageable);
    List<ImportJobRow> findAllByJobIdOrderByRowNumberAsc(UUID jobId);
    long countByJobId(UUID jobId);
}
