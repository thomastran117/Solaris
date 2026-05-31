package backend.repositories;

import backend.models.core.Report;
import backend.models.enums.ReportStatus;
import backend.models.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    Page<Report> findAll(Pageable pageable);

    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    Page<Report> findByTargetType(ReportTargetType targetType, Pageable pageable);

    Page<Report> findByTargetTypeAndStatus(ReportTargetType targetType, ReportStatus status, Pageable pageable);

    boolean existsByTargetTypeAndTargetIdAndReporterId(ReportTargetType targetType, UUID targetId, UUID reporterId);

    long countByReporterIdAndCreatedAtAfter(UUID reporterId, Instant after);
}
