package backend.services.impl.collections;

import backend.models.core.Collection;
import backend.models.enums.CollectionStatus;
import backend.models.enums.CollectionType;
import backend.repositories.CollectionRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DynamicCollectionWorkerTest {

    private CollectionRepository collectionRepository;
    private CollectionServiceImpl collectionService;
    private TransactionTemplate transactionTemplate;
    private DynamicCollectionWorker worker;

    @BeforeEach
    void setUp() {
        collectionRepository = mock(CollectionRepository.class);
        collectionService = mock(CollectionServiceImpl.class);
        transactionTemplate = mock(TransactionTemplate.class);

        // Make the transaction template execute the callback synchronously
        doAnswer(inv -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action =
                    inv.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        worker = new DynamicCollectionWorker(collectionRepository, collectionService, transactionTemplate);
    }

    // ─── refreshDynamicCollections ────────────────────────────────────────────

    @Test
    void refreshDynamicCollections_noDueCollections_doesNothing() {
        when(collectionRepository.findAllByTypeAndStatus(CollectionType.DYNAMIC, CollectionStatus.ACTIVE))
                .thenReturn(List.of());

        worker.refreshDynamicCollections();

        verifyNoInteractions(collectionService);
        verifyNoInteractions(transactionTemplate);
    }

    @Test
    void refreshDynamicCollections_callsMaterialiseForEachDynamicActiveCollection() {
        Collection c1 = makeCollection(TestIds.uuid(1), CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        Collection c2 = makeCollection(TestIds.uuid(2), CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findAllByTypeAndStatus(CollectionType.DYNAMIC, CollectionStatus.ACTIVE))
                .thenReturn(List.of(c1, c2));
        when(collectionRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(c1));
        when(collectionRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(c2));

        worker.refreshDynamicCollections();

        verify(collectionService).materialiseDynamicMembers(c1);
        verify(collectionService).materialiseDynamicMembers(c2);
        verify(collectionRepository).save(c1);
        verify(collectionRepository).save(c2);
    }

    @Test
    void refreshDynamicCollections_failingCollection_doesNotStopBatch() {
        Collection c1 = makeCollection(TestIds.uuid(1), CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        Collection c2 = makeCollection(TestIds.uuid(2), CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findAllByTypeAndStatus(CollectionType.DYNAMIC, CollectionStatus.ACTIVE))
                .thenReturn(List.of(c1, c2));
        when(collectionRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(c1));
        when(collectionRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(c2));

        // First collection throws during materialise
        doThrow(new RuntimeException("DB failure")).when(collectionService).materialiseDynamicMembers(c1);

        worker.refreshDynamicCollections();

        // Second collection is still processed
        verify(collectionService).materialiseDynamicMembers(c2);
    }

    @Test
    void refreshDynamicCollections_collectionDeletedBetweenFetchAndProcess_skips() {
        Collection c = makeCollection(TestIds.uuid(1), CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findAllByTypeAndStatus(CollectionType.DYNAMIC, CollectionStatus.ACTIVE))
                .thenReturn(List.of(c));
        // Collection no longer exists when reloaded inside the transaction
        when(collectionRepository.findById(TestIds.uuid(1))).thenReturn(Optional.empty());

        worker.refreshDynamicCollections();

        verifyNoInteractions(collectionService);
    }

    @Test
    void refreshDynamicCollections_collectionDowngradedToStatic_skips() {
        Collection listed = makeCollection(TestIds.uuid(1), CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        // Reloaded with STATIC type — was changed between the find and the reload
        Collection reloaded = makeCollection(TestIds.uuid(1), CollectionType.STATIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findAllByTypeAndStatus(CollectionType.DYNAMIC, CollectionStatus.ACTIVE))
                .thenReturn(List.of(listed));
        when(collectionRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(reloaded));

        worker.refreshDynamicCollections();

        verifyNoInteractions(collectionService);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Collection makeCollection(UUID id, CollectionType type, CollectionStatus status) {
        Collection c = new Collection();
        c.setId(id);
        c.setType(type);
        c.setStatus(status);
        return c;
    }
}
