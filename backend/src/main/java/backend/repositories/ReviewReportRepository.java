package backend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.ReviewReport;
import backend.models.enums.ReportStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewReportRepository extends JpaRepository<ReviewReport, UUID> {

    boolean existsByReviewIdAndReporterId(UUID reviewId, UUID reporterId);

    Page<ReviewReport> findByStatus(ReportStatus status, Pageable pageable);

    List<ReviewReport> findByReviewIdAndStatus(UUID reviewId, ReportStatus status);

    @Modifying
    @Query("UPDATE ReviewReport r SET r.status = :status, r.resolvedAt = :resolvedAt, r.resolvedBy = :resolvedBy " +
           "WHERE r.reviewId = :reviewId AND r.status = backend.models.enums.ReportStatus.OPEN")
    int resolveOpenReportsForReview(
            @Param("reviewId") UUID reviewId,
            @Param("status") ReportStatus status,
            @Param("resolvedAt") Instant resolvedAt,
            @Param("resolvedBy") UUID resolvedBy);
}
