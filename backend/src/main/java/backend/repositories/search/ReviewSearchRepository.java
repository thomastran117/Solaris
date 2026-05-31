package backend.repositories.search;

import backend.documents.ReviewDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReviewSearchRepository extends ElasticsearchRepository<ReviewDocument, UUID> {
}
