package backend.kafka.workers;

import backend.configurations.search.SearchIndexSettings;
import backend.models.core.IndexVersion;
import backend.repositories.IndexVersionRepository;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IndexVersionManagerTest {

    private ElasticsearchClient     esClient;
    private IndexVersionRepository  versionRepository;
    private SearchIndexSettings     searchIndexSettings;
    private IndexVersionManager     manager;

    @BeforeEach
    void setUp() {
        esClient            = mock(ElasticsearchClient.class, Mockito.RETURNS_DEEP_STUBS);
        versionRepository   = mock(IndexVersionRepository.class);
        searchIndexSettings = mock(SearchIndexSettings.class);
        // @Value not injected in plain unit tests — 'enabled' defaults to false (Java primitive default)
        manager = new IndexVersionManager(esClient, versionRepository, searchIndexSettings);
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
}
