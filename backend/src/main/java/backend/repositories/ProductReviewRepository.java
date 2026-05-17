package backend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.ProductReview;
import backend.models.enums.ReviewStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long>, JpaSpecificationExecutor<ProductReview> {
    Page<ProductReview> findAllByProductIdAndStatus(long productId, ReviewStatus status, Pageable pageable);
    Optional<ProductReview> findByProductIdAndReviewerId(long productId, long reviewerId);
    Optional<ProductReview> findByIdAndReviewerId(long id, long reviewerId);
    boolean existsByProductIdAndReviewerId(long productId, long reviewerId);

    @Query("SELECT r.product.id, AVG(r.rating), COUNT(r) FROM ProductReview r " +
           "WHERE r.product.id IN :productIds AND r.status = backend.models.enums.ReviewStatus.PUBLISHED " +
           "GROUP BY r.product.id")
    List<Object[]> findAverageRatingsByProductIds(@Param("productIds") List<Long> productIds);

    @Query("SELECT r.rating, COUNT(r) FROM ProductReview r " +
           "WHERE r.product.id = :productId AND r.status = backend.models.enums.ReviewStatus.PUBLISHED " +
           "GROUP BY r.rating")
    List<Object[]> findRatingDistribution(@Param("productId") long productId);

    @Query("SELECT AVG(r.rating), COUNT(r), " +
           "SUM(CASE WHEN r.verifiedPurchase = TRUE THEN 1 ELSE 0 END) " +
           "FROM ProductReview r " +
           "WHERE r.product.id = :productId AND r.status = backend.models.enums.ReviewStatus.PUBLISHED")
    Object[] findSummaryAggregates(@Param("productId") long productId);

    @Modifying
    @Query(value = "UPDATE product_reviews SET helpful_count = helpful_count + 1 WHERE id = :reviewId", nativeQuery = true)
    int incrementHelpfulCount(@Param("reviewId") long reviewId);

    @Modifying
    @Query(value = "UPDATE product_reviews SET helpful_count = GREATEST(helpful_count - 1, 0) WHERE id = :reviewId", nativeQuery = true)
    int decrementHelpfulCount(@Param("reviewId") long reviewId);

    @Modifying
    @Query(value = "UPDATE product_reviews SET report_count = report_count + 1 WHERE id = :reviewId", nativeQuery = true)
    int incrementReportCount(@Param("reviewId") long reviewId);

    @Modifying
    @Query(value = "UPDATE product_reviews SET status = :status WHERE id = :reviewId AND status <> :status", nativeQuery = true)
    int updateStatusIfDifferent(@Param("reviewId") long reviewId, @Param("status") String status);
}
