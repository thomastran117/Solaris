package backend.repositories;

import backend.models.core.ProductQuestion;
import backend.models.enums.QAStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductQuestionRepository extends JpaRepository<ProductQuestion, UUID> {
    Page<ProductQuestion> findByProductIdAndStatus(UUID productId, QAStatus status, Pageable pageable);

    @Modifying
    @Query(value = "UPDATE product_questions SET report_count = report_count + 1 WHERE id = :id", nativeQuery = true)
    int incrementReportCount(@Param("id") UUID id);

    @Modifying
    @Query(value = "UPDATE product_questions SET status = :status WHERE id = :id AND status <> :status", nativeQuery = true)
    int updateStatusIfDifferent(@Param("id") UUID id, @Param("status") String status);
}
