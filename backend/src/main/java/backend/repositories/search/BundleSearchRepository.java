package backend.repositories.search;

import backend.documents.BundleDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BundleSearchRepository extends ElasticsearchRepository<BundleDocument, UUID> {
}
