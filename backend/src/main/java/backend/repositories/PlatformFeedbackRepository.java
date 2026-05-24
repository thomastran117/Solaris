package backend.repositories;

import backend.models.core.PlatformFeedback;
import backend.models.enums.FeedbackCategory;
import backend.models.enums.FeedbackStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PlatformFeedbackRepository extends JpaRepository<PlatformFeedback, UUID> {

    Page<PlatformFeedback> findAllBySubmittedByIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("SELECT f FROM PlatformFeedback f WHERE " +
           "(:status IS NULL OR f.status = :status) AND " +
           "(:category IS NULL OR f.category = :category) " +
           "ORDER BY f.createdAt DESC")
    Page<PlatformFeedback> findAllByFilters(
            @Param("status") FeedbackStatus status,
            @Param("category") FeedbackCategory category,
            Pageable pageable);
}
