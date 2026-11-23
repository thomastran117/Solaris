package backend.kafka.workers;

import backend.configurations.search.SearchIndexSettings;
import backend.documents.ProductDocument;
import backend.models.core.IndexVersion;
import backend.repositories.IndexVersionRepository;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IndexVersionManagerTest {

    private ElasticsearchClient     esClient;
    private IndexVersionRepository  versionRepository;
    private SearchIndexSettings     searchIndexSettings;
    private ElasticsearchOperations elasticsearchOperations;
    private IndexOperations         entityOps;
    private Document                productMapping;
    private IndexVersionManager     manager;

    @BeforeEach
    void setUp() {
        esClient            = mock(ElasticsearchClient.class, Mockito.RETURNS_DEEP_STUBS);
        versionRepository   = mock(IndexVersionRepository.class);
        searchIndexSettings = mock(SearchIndexSettings.class);
        // RETURNS_MOCKS: indexOps(...) for an unstubbed index returns a no-op mock, so tests that
        // don't care about mapping still run the create/rollover path end to end.
        elasticsearchOperations = mock(ElasticsearchOperations.class, Mockito.RETURNS_MOCKS);
        entityOps = mock(IndexOperations.class);
        productMapping = Document.create();
        productMapping.put("properties", new java.util.HashMap<String, Object>());
        when(elasticsearchOperations.indexOps(ProductDocument.class)).thenReturn(entityOps);
        when(entityOps.createMapping()).thenReturn(productMapping);
        // @Value not injected in plain unit tests — 'enabled' defaults to false (Java primitive default)
        manager = new IndexVersionManager(esClient, versionRepository, searchIndexSettings, elasticsearchOperations);
    }

    /** Stubs indexOps(IndexCoordinates) for one physical index or alias name. */
    private IndexOperations opsFor(String indexName) {
        IndexOperations ops = mock(IndexOperations.class);
        when(elasticsearchOperations.indexOps(
                argThat((IndexCoordinates c) -> c != null && indexName.equals(c.getIndexName()))))
                .thenReturn(ops);
        return ops;
    }

    // ─── disabled (enabled=false is the Java default for uninjected @Value booleans) ─────

    @Test
    void ensureIndexExists_whenDisabled_noEsCallsMade() {
        manager.ensureIndexExists("products");

        verifyNoInteractions(esClient);
        verifyNoInteractions(versionRepository);
    }

    @Test
    void rolloverIndex_whenDisabled_noEsCallsMade() {
        manager.rolloverIndex("products");

        verifyNoInteractions(esClient);
        verifyNoInteractions(versionRepository);
    }

    // ─── ensureIndexExists — enabled, exception swallowed ────────────────────

    @Test
    void ensureIndexExists_esClientThrows_doesNotPropagate() {
        ReflectionTestUtils.setField(manager, "enabled", true);
        when(esClient.indices()).thenThrow(new RuntimeException("ES unavailable"));

        assertDoesNotThrow(() -> manager.ensureIndexExists("products"));
    }

    // ─── rolloverIndex — enabled, exception propagated ───────────────────────

    @Test
    void rolloverIndex_esClientThrows_propagatesAsRuntimeException() {
        ReflectionTestUtils.setField(manager, "enabled", true);
        when(versionRepository.findById(any(String.class))).thenReturn(Optional.empty());
        when(esClient.indices()).thenThrow(new RuntimeException("ES unavailable"));

        assertThrows(RuntimeException.class, () -> manager.rolloverIndex("products"));
    }

    // ─── ensureIndexExists — enabled, alias does not exist ──────────────────

    @Test
    void ensureIndexExists_aliasDoesNotExist_createsIndexAndSavesVersion() {
        ReflectionTestUtils.setField(manager, "enabled", true);
        // Default deep stub: existsAlias(...).value() returns false
        when(versionRepository.findById(anyString())).thenReturn(Optional.empty());
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> manager.ensureIndexExists("products"));

        verify(versionRepository).save(argThat(iv -> iv.getVersion() == 1 && "products_v1".equals(iv.getIndexName())));
    }

    // ─── ensureIndexExists — enabled, alias already exists ──────────────────────

    @Test
    void ensureIndexExists_aliasExists_withDbRecord_logsAndDoesNotCreateIndex() throws Exception {
        ReflectionTestUtils.setField(manager, "enabled", true);
        // Capture the deep-stub indices mock first, then stub existsAlias on it directly
        var indicesMock = esClient.indices();
        Mockito.doReturn(new BooleanResponse(true)).when(indicesMock)
                .existsAlias(any(java.util.function.Function.class));

        IndexVersion existing = new IndexVersion();
        existing.setAlias("products");
        existing.setVersion(2);
        existing.setIndexName("products_v2");
        when(versionRepository.findById("products")).thenReturn(Optional.of(existing));

        assertDoesNotThrow(() -> manager.ensureIndexExists("products"));

        // Should NOT save a new version — alias already exists
        verify(versionRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void ensureIndexExists_aliasExists_noDbRecord_logsWarnAndDoesNotCreateIndex() throws Exception {
        ReflectionTestUtils.setField(manager, "enabled", true);
        var indicesMock = esClient.indices();
        Mockito.doReturn(new BooleanResponse(true)).when(indicesMock)
                .existsAlias(any(java.util.function.Function.class));
        when(versionRepository.findById("products")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> manager.ensureIndexExists("products"));

        verify(versionRepository, org.mockito.Mockito.never()).save(any());
    }

    // ─── rolloverIndex — enabled, no current version (happy path via stubs) ──

    @Test
    void rolloverIndex_noCurrentVersion_savesVersionAfterSuccess() {
        ReflectionTestUtils.setField(manager, "enabled", true);
        when(versionRepository.findById(anyString())).thenReturn(Optional.empty());
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // All ES calls return deep stubs (no exception) — reindex lambda is never executed
        assertDoesNotThrow(() -> manager.rolloverIndex("products"));

        verify(versionRepository).save(argThat(iv -> iv.getVersion() == 2));
    }

    @Test
    void rolloverIndex_withCurrentVersion_incrementsVersionNumber() {
        ReflectionTestUtils.setField(manager, "enabled", true);

        IndexVersion current = new IndexVersion();
        current.setAlias("products");
        current.setVersion(3);
        current.setIndexName("products_v3");
        when(versionRepository.findById(anyString())).thenReturn(Optional.of(current));
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> manager.rolloverIndex("products"));

        verify(versionRepository).save(argThat(iv -> iv.getVersion() == 4));
    }

    // ─── mapping — new physical indices get the document mapping ─────────────

    @Test
    void ensureIndexExists_aliasDoesNotExist_appliesDocumentMappingToNewIndex() {
        ReflectionTestUtils.setField(manager, "enabled", true);
        when(versionRepository.findById(anyString())).thenReturn(Optional.empty());
        IndexOperations v1 = opsFor("products_v1");

        manager.ensureIndexExists("products");

        verify(v1).putMapping(productMapping);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rolloverIndex_appliesDocumentMappingBeforeCopyingDocuments() throws Exception {
        ReflectionTestUtils.setField(manager, "enabled", true);
        when(versionRepository.findById(anyString())).thenReturn(Optional.empty());
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        IndexOperations v2 = opsFor("products_v2");

        manager.rolloverIndex("products");

        // Mapping first: a settings-only index would dynamically map the copied documents.
        InOrder order = inOrder(v2, esClient);
        order.verify(v2).putMapping(productMapping);
        order.verify(esClient).reindex(any(java.util.function.Function.class));
    }

    // ─── applyCurrentMapping — additive migration of the live index ───────────

    private static final Map<String, Object> NAME_TEXT_ONLY =
            Map.of("type", "text", "search_analyzer", "product_search");
    private static final Map<String, Object> NAME_WITH_SORT = Map.of(
            "type", "text", "search_analyzer", "product_search",
            "fields", Map.of("sort", Map.of("type", "keyword", "ignore_above", 256)));
    private static final Map<String, Object> CREATED_AT =
            Map.of("type", "date", "format", "date_optional_time||epoch_millis");

    /** A live index shaped like one deployed before #61: no createdAt, name without name.sort. */
    private IndexOperations liveProductsIndex() {
        IndexOperations live = opsFor("products");
        when(live.exists()).thenReturn(true);
        Document liveMapping = Document.create();
        liveMapping.put("properties", Map.of(
                "name", NAME_TEXT_ONLY,
                // Legacy format differs from what the entity generates — must not block additions.
                "publishedAt", Map.of("type", "date")));
        when(live.getMapping()).thenReturn(liveMapping);
        productMapping.put("properties", Map.of(
                "name", NAME_WITH_SORT,
                "publishedAt", CREATED_AT,
                "createdAt", CREATED_AT));
        return live;
    }

    @SuppressWarnings("unchecked")
    private static java.util.Set<String> putFieldNames(IndexOperations live) {
        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(live, org.mockito.Mockito.atLeast(0)).putMapping(captor.capture());
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Document d : captor.getAllValues()) {
            names.addAll(((Map<String, Object>) d.get("properties")).keySet());
        }
        return names;
    }

    @Test
    void applyCurrentMapping_addsOnlyMissingFieldsAndNewMultiFields() {
        IndexOperations live = liveProductsIndex();

        manager.applyCurrentMapping("products");

        // name gains name.sort; createdAt is new; publishedAt already exists and is left alone even
        // though its format differs — restating it would make Elasticsearch reject the request.
        assertEquals(java.util.Set.of("name", "createdAt"), putFieldNames(live));
    }

    @Test
    void applyCurrentMapping_sendsOneFieldPerRequest_soAConflictDoesNotBlockTheRest() {
        IndexOperations live = liveProductsIndex();
        when(live.putMapping(argThat((Document d) -> d != null
                && ((Map<?, ?>) d.get("properties")).containsKey("name"))))
                .thenThrow(new RuntimeException("mapper [name] conflicts with existing mapper"));

        assertDoesNotThrow(() -> manager.applyCurrentMapping("products"));

        assertTrue(putFieldNames(live).contains("createdAt"), "createdAt is still added after name conflicts");
    }

    @Test
    void applyCurrentMapping_runsEvenWhenVersioningDisabled() {
        // enabled=false by default here; a deployment without versioned aliases still needs new fields.
        IndexOperations live = liveProductsIndex();

        manager.applyCurrentMapping("products");

        assertFalse(putFieldNames(live).isEmpty());
    }

    @Test
    void applyCurrentMapping_upToDateIndex_putsNothing() {
        IndexOperations live = opsFor("products");
        when(live.exists()).thenReturn(true);
        Document current = Document.create();
        current.put("properties", Map.of("name", NAME_WITH_SORT, "createdAt", CREATED_AT));
        when(live.getMapping()).thenReturn(current);
        productMapping.put("properties", Map.of("name", NAME_WITH_SORT, "createdAt", CREATED_AT));

        manager.applyCurrentMapping("products");

        verify(live, never()).putMapping(any(Document.class));
    }

    @Test
    void applyCurrentMapping_missingIndex_doesNothing() {
        IndexOperations live = opsFor("products");
        when(live.exists()).thenReturn(false);

        manager.applyCurrentMapping("products");

        verify(live, never()).putMapping(any(Document.class));
    }

    @Test
    void applyCurrentMapping_mappingReadFails_doesNotPropagate() {
        IndexOperations live = opsFor("products");
        when(live.exists()).thenReturn(true);
        when(live.getMapping()).thenThrow(new RuntimeException("ES unavailable"));

        assertDoesNotThrow(() -> manager.applyCurrentMapping("products"));
        verify(live, never()).putMapping(any(Document.class));
    }

    @Test
    void applyCurrentMapping_unknownAlias_noEsCalls() {
        manager.applyCurrentMapping("reports");

        verifyNoInteractions(elasticsearchOperations);
    }

    @Test
    void missingProperties_ignoresFieldsWhoseSubFieldsArePresent() {
        Map<String, Object> current = Map.of("name", NAME_WITH_SORT, "price", Map.of("type", "double"));
        Map<String, Object> wanted = Map.of("name", NAME_WITH_SORT, "price", Map.of("type", "double"),
                "stock", Map.of("type", "integer"));

        assertEquals(java.util.Set.of("stock"), IndexVersionManager.missingProperties(current, wanted).keySet());
    }
}
