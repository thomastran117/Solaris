package backend.repositories;

import backend.models.core.ImportJobRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportJobRowRepository extends JpaRepository<ImportJobRow, Long> {
    Page<ImportJobRow> findAllByJobIdOrderByRowNumberAsc(long jobId, Pageable pageable);
    List<ImportJobRow> findAllByJobIdOrderByRowNumberAsc(long jobId);
    long countByJobId(long jobId);
}
