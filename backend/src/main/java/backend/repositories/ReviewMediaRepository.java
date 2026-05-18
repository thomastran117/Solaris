package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.models.core.ReviewMedia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewMediaRepository extends JpaRepository<ReviewMedia, UUID> {

    List<ReviewMedia> findByReviewIdOrderByPositionAsc(UUID reviewId);

    List<ReviewMedia> findByReviewIdInOrderByReviewIdAscPositionAsc(Collection<UUID> reviewIds);

    long countByReviewId(UUID reviewId);

    Optional<ReviewMedia> findByIdAndReviewId(UUID id, UUID reviewId);
}
