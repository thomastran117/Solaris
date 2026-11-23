package backend.kafka.workers;

import backend.configurations.search.SearchIndexSettings;
import backend.documents.BundleDocument;
import backend.documents.ProductDocument;
import backend.models.core.IndexVersion;
import backend.repositories.IndexVersionRepository;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Manages versioned Elasticsearch index aliases.
 *
 * Each logical index (e.g. "products") maps to a physical index ("products_v1", "products_v2", …)
 * via an ES alias. This enables zero-downtime mapping changes: create a new versioned index,
 * reindex data into it, then atomically swap the alias.
 *
 * The current version is tracked in the {@code index_versions} DB table so the application
 * can validate that ES and DB state are consistent on startup.
 *
 * Every physical index this class creates gets the field mapping generated from its document class
 * before any document is written, so a rollover produces correctly typed fields (keyword, date,
 * multi-fields such as {@code name.sort}) rather than whatever dynamic mapping infers.
 */
@Component
public class IndexVersionManager {

    private static final Logger log = LoggerFactory.getLogger(IndexVersionManager.class);

    @Value("${app.elasticsearch.versioning.enabled:true}")
    private boolean enabled;

    /** Logical index (alias) → the document class whose annotations define its field mapping. */
    private static final Map<String, Class<?>> DOCUMENT_TYPES = Map.of(
            "products", ProductDocument.class,
            "bundles",  BundleDocument.class);

    private final ElasticsearchClient esClient;
    private final IndexVersionRepository versionRepository;
    private final SearchIndexSettings searchIndexSettings;
    private final ElasticsearchOperations elasticsearchOperations;

    public IndexVersionManager(ElasticsearchClient esClient, IndexVersionRepository versionRepository,
                                SearchIndexSettings searchIndexSettings,
                                ElasticsearchOperations elasticsearchOperations) {
        this.esClient = esClient;
        this.versionRepository = versionRepository;
        this.searchIndexSettings = searchIndexSettings;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    /**
     * Called on startup: additively applies the current document mapping to the live index (alias
     * or concrete index). This is how new fields and multi-fields added to a document class reach an
     * index that already exists. Existing documents gain values for them when they are next written,
     * e.g. by {@code POST /admin/search/reindex}.
     *
     * <p>Elasticsearch rejects changes to an existing field's type. When the live mapping conflicts
     * (typically an index that was dynamically mapped), this logs a warning instead of failing
     * startup; the reindex endpoint's rollover then migrates to a freshly mapped index.
     */
    public void applyCurrentMapping(String alias) {
        Class<?> documentType = DOCUMENT_TYPES.get(alias);
        if (documentType == null) return;
        try {
            if (!elasticsearchOperations.indexOps(IndexCoordinates.of(alias)).exists()) return;
            putMapping(alias, documentType);
            log.info("[INDEX VERSION] Applied current {} mapping to '{}'", documentType.getSimpleName(), alias);
        } catch (Exception e) {
            log.warn("[INDEX VERSION] Could not apply current mapping to '{}' — run POST /admin/search/reindex "
                    + "to roll over to a correctly mapped index: {}", alias, e.getMessage());
        }
    }

    private void putMapping(String indexName, Class<?> documentType) {
        var mapping = elasticsearchOperations.indexOps(documentType).createMapping();
        elasticsearchOperations.indexOps(IndexCoordinates.of(indexName)).putMapping(mapping);
    }

    /** Applies the alias's document mapping to a newly created, still empty, physical index. */
    private void mapNewIndex(String alias, String indexName) {
        Class<?> documentType = DOCUMENT_TYPES.get(alias);
        if (documentType != null) putMapping(indexName, documentType);
    }

    /**
     * Called on startup: ensures the physical index and alias exist.
     * If neither exists, creates {@code {alias}_v1} and points the alias to it.
     * If the alias already exists in ES, verifies the DB record is consistent.
     */
    @Transactional
    public void ensureIndexExists(String alias) {
        if (!enabled) return;
        try {
            boolean aliasExists = esClient.indices().existsAlias(a -> a.name(alias)).value();
            if (!aliasExists) {
                String indexName = alias + "_v1";
                esClient.indices().create(c -> c
                        .index(indexName)
                        .settings(searchIndexSettings.buildSettings()));
                mapNewIndex(alias, indexName);
                esClient.indices().putAlias(a -> a.index(indexName).name(alias));
                saveVersion(alias, 1, indexName);
                log.info("[INDEX VERSION] Created index '{}' with alias '{}'", indexName, alias);
            } else {
                versionRepository.findById(alias).ifPresentOrElse(
                        v -> log.info("[INDEX VERSION] Alias '{}' → '{}' (v{})", alias, v.getIndexName(), v.getVersion()),
                        () -> log.warn("[INDEX VERSION] Alias '{}' exists in ES but has no DB record — run rolloverIndex to reconcile", alias)
                );
            }
        } catch (Exception e) {
            log.warn("[INDEX VERSION] ensureIndexExists for '{}' failed: {}", alias, e.getMessage());
        }
    }

    /**
     * Creates the next versioned index, reindexes all documents from the current index,
     * atomically swaps the alias, then deletes the old index.
     * Safe to call while the application is live — reads continue against the old index
     * until the alias swap completes.
     */
    @Transactional
    public void rolloverIndex(String alias) {
        if (!enabled) {
            log.warn("[INDEX VERSION] Versioning is disabled — rolloverIndex skipped for '{}'", alias);
            return;
        }
        try {
            IndexVersion current = versionRepository.findById(alias).orElse(null);
            int newVersion = current != null ? current.getVersion() + 1 : 2;
            String oldIndexName = current != null ? current.getIndexName() : (alias + "_v1");
            String newIndexName = alias + "_v" + newVersion;

            log.info("[INDEX VERSION] Rolling over '{}': {} → {}", alias, oldIndexName, newIndexName);

            esClient.indices().create(c -> c
                    .index(newIndexName)
                    .settings(searchIndexSettings.buildSettings()));
            // Map before copying documents in, so reindexed fields get the declared types.
            mapNewIndex(alias, newIndexName);

            esClient.reindex(r -> r
                    .source(s -> s.index(oldIndexName))
                    .dest(d -> d.index(newIndexName)));

            // Atomic alias swap: remove old, add new
            esClient.indices().updateAliases(UpdateAliasesRequest.of(u -> u
                    .actions(a -> a.remove(rem -> rem.index(oldIndexName).alias(alias)))
                    .actions(a -> a.add(add -> add.index(newIndexName).alias(alias)))));

            esClient.indices().delete(d -> d.index(oldIndexName));

            saveVersion(alias, newVersion, newIndexName);
            log.info("[INDEX VERSION] Rollover complete for '{}' — now on {}", alias, newIndexName);
        } catch (Exception e) {
            log.error("[INDEX VERSION] Rollover failed for '{}': {}", alias, e.getMessage());
            throw new RuntimeException("Index rollover failed for alias '" + alias + "'", e);
        }
    }

    private void saveVersion(String alias, int version, String indexName) {
        IndexVersion v = versionRepository.findById(alias).orElse(new IndexVersion());
        v.setAlias(alias);
        v.setVersion(version);
        v.setIndexName(indexName);
        v.setUpdatedAt(Instant.now());
        versionRepository.save(v);
    }
}
