package backend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.models.core.ProductReview;
import backend.models.enums.ReviewStatus;

import java.util.Optional;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    Page<ProductReview> findAllByProductIdAndStatus(long productId, ReviewStatus status, Pageable pageable);
    Optional<ProductReview> findByProductIdAndReviewerId(long productId, long reviewerId);
    Optional<ProductReview> findByIdAndReviewerId(long id, long reviewerId);
    boolean existsByProductIdAndReviewerId(long productId, long reviewerId);
}
