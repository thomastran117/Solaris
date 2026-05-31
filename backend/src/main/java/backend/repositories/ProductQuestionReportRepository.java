package backend.repositories;

import backend.models.core.ProductQuestionReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductQuestionReportRepository extends JpaRepository<ProductQuestionReport, UUID> {
}
