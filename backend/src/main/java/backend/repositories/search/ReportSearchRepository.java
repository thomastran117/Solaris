package backend.repositories.search;

import backend.documents.ReportDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReportSearchRepository extends ElasticsearchRepository<ReportDocument, UUID> {
}
