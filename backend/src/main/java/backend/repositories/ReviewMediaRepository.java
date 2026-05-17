package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.models.core.ReviewMedia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewMediaRepository extends JpaRepository<ReviewMedia, Long> {

    List<ReviewMedia> findByReviewIdOrderByPositionAsc(long reviewId);

    List<ReviewMedia> findByReviewIdInOrderByReviewIdAscPositionAsc(Collection<Long> reviewIds);

    long countByReviewId(long reviewId);

    Optional<ReviewMedia> findByIdAndReviewId(long id, long reviewId);
}
