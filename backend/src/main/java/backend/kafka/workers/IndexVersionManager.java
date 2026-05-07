package backend.kafka.workers;

import backend.configurations.search.SearchIndexSettings;
import backend.models.core.IndexVersion;
import backend.repositories.IndexVersionRepository;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Manages versioned Elasticsearch index aliases.
 *
 * Each logical index (e.g. "products") maps to a physical index ("products_v1", "products_v2", …)
 * via an ES alias. This enables zero-downtime mapping changes: create a new versioned index,
 * reindex data into it, then atomically swap the alias.
 *
 * The current version is tracked in the {@code index_versions} DB table so the application
 * can validate that ES and DB state are consistent on startup.
 */
@Component
public class IndexVersionManager {

    private static final Logger log = LoggerFactory.getLogger(IndexVersionManager.class);

    @Value("${app.elasticsearch.versioning.enabled:true}")
    private boolean enabled;

    private final ElasticsearchClient esClient;
    private final IndexVersionRepository versionRepository;
    private final SearchIndexSettings searchIndexSettings;

    public IndexVersionManager(ElasticsearchClient esClient, IndexVersionRepository versionRepository,
                                SearchIndexSettings searchIndexSettings) {
        this.esClient = esClient;
        this.versionRepository = versionRepository;
        this.searchIndexSettings = searchIndexSettings;
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
