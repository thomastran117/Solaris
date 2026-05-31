package backend.repositories;

import backend.models.core.CompanyAnnouncement;
import backend.models.enums.AnnouncementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyAnnouncementRepository extends JpaRepository<CompanyAnnouncement, UUID> {

    Page<CompanyAnnouncement> findAllByCompanyIdAndStatus(UUID companyId, AnnouncementStatus status, Pageable pageable);

    Page<CompanyAnnouncement> findAllByCompanyId(UUID companyId, Pageable pageable);

    Optional<CompanyAnnouncement> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query("""
            SELECT a FROM CompanyAnnouncement a
            WHERE a.company.id IN (
                SELECT f.company.id FROM CompanyFollow f WHERE f.user.id = :userId
            )
            AND a.status = 'PUBLISHED'
            ORDER BY a.publishedAt DESC
            """)
    Page<CompanyAnnouncement> findFeedForUser(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT COUNT(a) FROM CompanyAnnouncement a WHERE a.company.id = :companyId AND a.createdAt >= :since")
    long countByCompanyIdAndCreatedAtAfter(@Param("companyId") UUID companyId, @Param("since") Instant since);
}
