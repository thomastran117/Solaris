package backend.repositories;

import backend.models.core.ProductAnswer;
import backend.models.enums.QAStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductAnswerRepository extends JpaRepository<ProductAnswer, UUID> {
    List<ProductAnswer> findByQuestionIdAndStatusOrderByUpvoteCountDesc(UUID questionId, QAStatus status);

    @Modifying
    @Query(value = "UPDATE product_answers SET upvote_count = upvote_count + 1 WHERE id = :id", nativeQuery = true)
    int incrementUpvoteCount(@Param("id") UUID id);

    @Modifying
    @Query(value = "UPDATE product_answers SET report_count = report_count + 1 WHERE id = :id", nativeQuery = true)
    int incrementReportCount(@Param("id") UUID id);

    @Modifying
    @Query(value = "UPDATE product_answers SET status = :status WHERE id = :id AND status <> :status", nativeQuery = true)
    int updateStatusIfDifferent(@Param("id") UUID id, @Param("status") String status);
}
