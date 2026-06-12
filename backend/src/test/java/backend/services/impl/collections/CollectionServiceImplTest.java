package backend.services.impl.collections;

import backend.dtos.requests.collection.AddCollectionProductRequest;
import backend.dtos.requests.collection.CreateCollectionRequest;
import backend.dtos.requests.collection.UpdateCollectionProductRequest;
import backend.dtos.requests.collection.UpdateCollectionRequest;
import backend.dtos.responses.collection.CollectionResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.events.ProductIndexEvent;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Collection;
import backend.models.core.CollectionProduct;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.enums.CollectionMembershipSource;
import backend.models.enums.CollectionStatus;
import backend.models.enums.CollectionType;
import backend.models.enums.CompanyCapability;
import backend.models.enums.ProductStatus;
import backend.repositories.CollectionProductRepository;
import backend.repositories.CollectionRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.ProductRepository;
import backend.services.impl.SingleFlightCache;
import backend.services.impl.pricing.ActivePromotionLookupService;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CollectionServiceImplTest {

    private CollectionRepository collectionRepository;
    private CollectionProductRepository collectionProductRepository;
    private ProductRepository productRepository;
    private CompanyRepository companyRepository;
    private MarketplaceProfileRepository marketplaceProfileRepository;
    private ApplicationEventPublisher eventPublisher;
    private SingleFlightCache singleFlightCache;
    private ActivePromotionLookupService activePromotionLookupService;
    private CompanyAccessService companyAccessService;
    private CollectionServiceImpl service;

    private static final UUID COMPANY_ID   = TestIds.uuid(1);
    private static final UUID OWNER_ID     = TestIds.uuid(2);
    private static final UUID COLL_ID      = TestIds.uuid(3);
    private static final UUID PRODUCT_ID   = TestIds.uuid(4);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(5);

    @BeforeEach
    void setUp() {
        collectionRepository       = mock(CollectionRepository.class);
        collectionProductRepository = mock(CollectionProductRepository.class);
        productRepository          = mock(ProductRepository.class);
        companyRepository          = mock(CompanyRepository.class);
        marketplaceProfileRepository = mock(MarketplaceProfileRepository.class);
        eventPublisher             = mock(ApplicationEventPublisher.class);
        singleFlightCache          = mock(SingleFlightCache.class);
        activePromotionLookupService = mock(ActivePromotionLookupService.class);
        companyAccessService       = mock(CompanyAccessService.class);

        service = new CollectionServiceImpl(
                collectionRepository, collectionProductRepository,
                productRepository, companyRepository, marketplaceProfileRepository,
                eventPublisher, singleFlightCache, activePromotionLookupService,
                new ObjectMapper(), companyAccessService);

        // Default stubs used across many tests
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(true);
        when(companyAccessService.require(eq(COMPANY_ID), eq(OWNER_ID), any(CompanyCapability.class)))
                .thenReturn(makeCompany(COMPANY_ID));
        when(collectionProductRepository.countByCollectionId(any())).thenReturn(0L);
        when(activePromotionLookupService.findForProducts(any())).thenReturn(Map.of());
    }

    // ─── listCollections ─────────────────────────────────────────────────────

    @Test
    void listCollections_companyNotFound_throwsResourceNotFound() {
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.listCollections(COMPANY_ID, null, null, null, 0, 20));
    }

    @Test
    void listCollections_noFilter_queriesAll() {
        when(collectionRepository.findAllByCompanyId(eq(COMPANY_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listCollections(COMPANY_ID, null, null, null, 0, 20);

        verify(collectionRepository).findAllByCompanyId(eq(COMPANY_ID), any());
        verify(collectionRepository, never()).findAllByCompanyIdAndType(any(), any(), any());
    }

    @Test
    void listCollections_typeFilter_queriesByType() {
        when(collectionRepository.findAllByCompanyIdAndType(eq(COMPANY_ID), eq(CollectionType.DYNAMIC), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listCollections(COMPANY_ID, CollectionType.DYNAMIC, null, null, 0, 20);

        verify(collectionRepository).findAllByCompanyIdAndType(eq(COMPANY_ID), eq(CollectionType.DYNAMIC), any());
    }

    @Test
    void listCollections_statusFilter_queriesByStatus() {
        when(collectionRepository.findAllByCompanyIdAndStatus(eq(COMPANY_ID), eq(CollectionStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listCollections(COMPANY_ID, null, CollectionStatus.ACTIVE, null, 0, 20);

        verify(collectionRepository).findAllByCompanyIdAndStatus(eq(COMPANY_ID), eq(CollectionStatus.ACTIVE), any());
    }

    @Test
    void listCollections_featuredFilter_appliedInMemory() {
        Collection featured = makeCollection(TestIds.uuid(10), CollectionType.STATIC, CollectionStatus.ACTIVE);
        featured.setFeatured(true);
        Collection nonFeatured = makeCollection(TestIds.uuid(11), CollectionType.STATIC, CollectionStatus.ACTIVE);
        nonFeatured.setFeatured(false);
        when(collectionRepository.findAllByCompanyId(eq(COMPANY_ID), any()))
                .thenReturn(new PageImpl<>(List.of(featured, nonFeatured)));

        PagedResponse<CollectionResponse> result =
                service.listCollections(COMPANY_ID, null, null, true, 0, 20);

        assertEquals(1, result.getItems().size());
        assertTrue(result.getItems().get(0).featured());
    }

    @Test
    void listCollections_clampsPageSizeTo50() {
        when(collectionRepository.findAllByCompanyId(eq(COMPANY_ID), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listCollections(COMPANY_ID, null, null, null, 0, 200);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(collectionRepository).findAllByCompanyId(eq(COMPANY_ID), captor.capture());
        assertEquals(50, captor.getValue().getPageSize());
    }

    // ─── getCollection ───────────────────────────────────────────────────────

    @Test
    void getCollection_companyNotFound_throwsResourceNotFound() {
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.getCollection(COMPANY_ID, COLL_ID));
    }

    @Test
    void getCollection_collectionNotFound_throwsResourceNotFound() {
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getCollection(COMPANY_ID, COLL_ID));
    }

    @Test
    void getCollection_returnsResponse() {
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));

        CollectionResponse resp = service.getCollection(COMPANY_ID, COLL_ID);

        assertEquals(COLL_ID, resp.id());
        assertEquals("STATIC", resp.type());
    }

    // ─── createCollection ────────────────────────────────────────────────────

    @Test
    void createCollection_slugConflict_throwsConflict() {
        when(collectionRepository.existsBySlugAndCompanyId("my-slug", COMPANY_ID)).thenReturn(true);

        CreateCollectionRequest req = makeCreateRequest("My Collection", "My-Slug", CollectionType.STATIC, null);

        assertThrows(ConflictException.class,
                () -> service.createCollection(COMPANY_ID, OWNER_ID, req));
        verify(collectionRepository, never()).save(any());
    }

    @Test
    void createCollection_normalisesSlug() {
        CreateCollectionRequest req = makeCreateRequest("Name", " My-Slug ", CollectionType.STATIC, null);
        when(collectionRepository.existsBySlugAndCompanyId("my-slug", COMPANY_ID)).thenReturn(false);
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createCollection(COMPANY_ID, OWNER_ID, req);

        ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(Collection.class);
        verify(collectionRepository).save(captor.capture());
        assertEquals("my-slug", captor.getValue().getSlug());
    }

    @Test
    void createCollection_staticCollection_savesProperly() {
        CreateCollectionRequest req = makeCreateRequest("Best Sellers", "best-sellers", CollectionType.STATIC, null);
        req.setStatus(CollectionStatus.ACTIVE);
        when(collectionRepository.existsBySlugAndCompanyId(any(), any())).thenReturn(false);
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CollectionResponse resp = service.createCollection(COMPANY_ID, OWNER_ID, req);

        assertEquals("best-sellers", resp.slug());
        assertEquals("STATIC", resp.type());
        verify(collectionRepository).save(any());
    }

    @Test
    void createCollection_defaultsTypeToStaticAndStatusToDraft() {
        CreateCollectionRequest req = new CreateCollectionRequest();
        req.setName("Test");
        req.setSlug("test");
        req.setType(null);
        req.setStatus(null);
        when(collectionRepository.existsBySlugAndCompanyId(any(), any())).thenReturn(false);
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createCollection(COMPANY_ID, OWNER_ID, req);

        ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(Collection.class);
        verify(collectionRepository).save(captor.capture());
        assertEquals(CollectionType.STATIC, captor.getValue().getType());
        assertEquals(CollectionStatus.DRAFT, captor.getValue().getStatus());
    }

    @Test
    void createCollection_dynamicCollection_invalidRules_throwsBadRequest() {
        CreateCollectionRequest req = makeCreateRequest("Dyn", "dyn-coll", CollectionType.DYNAMIC, null);

        assertThrows(BadRequestException.class,
                () -> service.createCollection(COMPANY_ID, OWNER_ID, req));
        verify(collectionRepository, never()).save(any());
    }

    @Test
    void createCollection_dynamicCollection_active_materialises() {
        String rules = "{\"tagsAnyOf\":[\"sale\"]}";
        CreateCollectionRequest req = makeCreateRequest("Sale Items", "sale-items", CollectionType.DYNAMIC, rules);
        req.setStatus(CollectionStatus.ACTIVE);
        when(collectionRepository.existsBySlugAndCompanyId(any(), any())).thenReturn(false);
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // No products match
        when(productRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(collectionProductRepository.findAllByCollectionIdAndSource(any(), eq(CollectionMembershipSource.AUTO)))
                .thenReturn(List.of());

        service.createCollection(COMPANY_ID, OWNER_ID, req);

        // Materialise was called — verify by the spec query being invoked
        verify(productRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class));
    }

    @Test
    void createCollection_dynamicCollection_draft_doesNotMaterialise() {
        String rules = "{\"tagsAnyOf\":[\"sale\"]}";
        CreateCollectionRequest req = makeCreateRequest("Dyn Draft", "dyn-draft", CollectionType.DYNAMIC, rules);
        req.setStatus(CollectionStatus.DRAFT);
        when(collectionRepository.existsBySlugAndCompanyId(any(), any())).thenReturn(false);
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createCollection(COMPANY_ID, OWNER_ID, req);

        verifyNoInteractions(productRepository);
    }

    // ─── updateCollection ────────────────────────────────────────────────────

    @Test
    void updateCollection_collectionNotFound_throwsResourceNotFound() {
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateCollection(COMPANY_ID, COLL_ID, OWNER_ID, new UpdateCollectionRequest()));
    }

    @Test
    void updateCollection_slugConflict_throwsConflict() {
        Collection existing = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        existing.setSlug("old-slug");
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(collectionRepository.existsBySlugAndCompanyId("new-slug", COMPANY_ID)).thenReturn(true);

        UpdateCollectionRequest req = new UpdateCollectionRequest();
        req.setSlug("new-slug");

        assertThrows(ConflictException.class,
                () -> service.updateCollection(COMPANY_ID, COLL_ID, OWNER_ID, req));
    }

    @Test
    void updateCollection_sameSlug_doesNotCheckConflict() {
        Collection existing = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        existing.setSlug("same-slug");
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCollectionRequest req = new UpdateCollectionRequest();
        req.setSlug("same-slug");

        service.updateCollection(COMPANY_ID, COLL_ID, OWNER_ID, req);

        verify(collectionRepository, never()).existsBySlugAndCompanyId(any(), any());
    }

    @Test
    void updateCollection_dynamicToStatic_deletesAutoRows() {
        Collection existing = makeCollection(COLL_ID, CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        existing.setRulesJson("{\"tagsAnyOf\":[\"x\"]}");
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCollectionRequest req = new UpdateCollectionRequest();
        req.setType(CollectionType.STATIC);

        service.updateCollection(COMPANY_ID, COLL_ID, OWNER_ID, req);

        verify(collectionProductRepository).deleteByCollectionIdAndSource(COLL_ID, CollectionMembershipSource.AUTO);
    }

    @Test
    void updateCollection_rulesChanged_active_rematerialises() {
        Collection existing = makeCollection(COLL_ID, CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        existing.setRulesJson("{\"tagsAnyOf\":[\"old\"]}");
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(collectionProductRepository.findAllByCollectionIdAndSource(any(), eq(CollectionMembershipSource.AUTO)))
                .thenReturn(List.of());

        UpdateCollectionRequest req = new UpdateCollectionRequest();
        req.setRulesJson("{\"tagsAnyOf\":[\"new\"]}");

        service.updateCollection(COMPANY_ID, COLL_ID, OWNER_ID, req);

        verify(productRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class));
    }

    @Test
    void updateCollection_updatesProvidedFields() {
        Collection existing = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.DRAFT);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCollectionRequest req = new UpdateCollectionRequest();
        req.setName("New Name");
        req.setStatus(CollectionStatus.ACTIVE);

        service.updateCollection(COMPANY_ID, COLL_ID, OWNER_ID, req);

        ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(Collection.class);
        verify(collectionRepository).save(captor.capture());
        assertEquals("New Name", captor.getValue().getName());
        assertEquals(CollectionStatus.ACTIVE, captor.getValue().getStatus());
    }

    // ─── deleteCollection ────────────────────────────────────────────────────

    @Test
    void deleteCollection_collectionNotFound_throwsResourceNotFound() {
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteCollection(COMPANY_ID, COLL_ID, OWNER_ID));
    }

    @Test
    void deleteCollection_deletesManualAutoRowsAndCollection() {
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));
        when(collectionProductRepository.findAllByCollectionId(COLL_ID)).thenReturn(List.of());
        when(productRepository.findAllByIdInAndCompanyId(any(), eq(COMPANY_ID))).thenReturn(List.of());

        service.deleteCollection(COMPANY_ID, COLL_ID, OWNER_ID);

        verify(collectionProductRepository).deleteByCollectionIdAndSource(COLL_ID, CollectionMembershipSource.MANUAL);
        verify(collectionProductRepository).deleteByCollectionIdAndSource(COLL_ID, CollectionMembershipSource.AUTO);
        verify(collectionRepository).delete(c);
    }

    @Test
    void deleteCollection_reindexesAffectedProducts() {
        Product p = makeProduct(PRODUCT_ID, COMPANY_ID);
        CollectionProduct cp = makeCollectionProduct(TestIds.uuid(20), COLL_ID, p);
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));
        when(collectionProductRepository.findAllByCollectionId(COLL_ID)).thenReturn(List.of(cp));
        when(productRepository.findAllByIdInAndCompanyId(any(), eq(COMPANY_ID))).thenReturn(List.of(p));

        service.deleteCollection(COMPANY_ID, COLL_ID, OWNER_ID);

        verify(eventPublisher).publishEvent(any(ProductIndexEvent.class));
    }

    // ─── addCollectionProduct ────────────────────────────────────────────────

    @Test
    void addCollectionProduct_dynamicCollection_throwsBadRequest() {
        Collection c = makeCollection(COLL_ID, CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));

        AddCollectionProductRequest req = new AddCollectionProductRequest();
        req.setProductId(PRODUCT_ID);

        assertThrows(BadRequestException.class,
                () -> service.addCollectionProduct(COMPANY_ID, COLL_ID, OWNER_ID, req));
    }

    @Test
    void addCollectionProduct_productNotFound_throwsResourceNotFound() {
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        AddCollectionProductRequest req = new AddCollectionProductRequest();
        req.setProductId(PRODUCT_ID);

        assertThrows(ResourceNotFoundException.class,
                () -> service.addCollectionProduct(COMPANY_ID, COLL_ID, OWNER_ID, req));
    }

    @Test
    void addCollectionProduct_duplicate_throwsConflict() {
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        Product p = makeProduct(PRODUCT_ID, COMPANY_ID);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(p));
        when(collectionProductRepository.existsByCollectionIdAndProductId(COLL_ID, PRODUCT_ID)).thenReturn(true);

        AddCollectionProductRequest req = new AddCollectionProductRequest();
        req.setProductId(PRODUCT_ID);

        assertThrows(ConflictException.class,
                () -> service.addCollectionProduct(COMPANY_ID, COLL_ID, OWNER_ID, req));
    }

    @Test
    void addCollectionProduct_savesWithManualSource() {
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        Product p = makeProduct(PRODUCT_ID, COMPANY_ID);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(p));
        when(collectionProductRepository.existsByCollectionIdAndProductId(COLL_ID, PRODUCT_ID)).thenReturn(false);
        when(productRepository.findAllByIdInAndCompanyId(any(), eq(COMPANY_ID))).thenReturn(List.of(p));
        when(collectionProductRepository.save(any())).thenAnswer(inv -> {
            CollectionProduct cp = inv.getArgument(0);
            cp.setId(TestIds.uuid(99));
            return cp;
        });

        AddCollectionProductRequest req = new AddCollectionProductRequest();
        req.setProductId(PRODUCT_ID);
        req.setBoostWeight(5);
        req.setPinnedRank(1);

        service.addCollectionProduct(COMPANY_ID, COLL_ID, OWNER_ID, req);

        ArgumentCaptor<CollectionProduct> captor = ArgumentCaptor.forClass(CollectionProduct.class);
        verify(collectionProductRepository).save(captor.capture());
        assertEquals(CollectionMembershipSource.MANUAL, captor.getValue().getSource());
        assertEquals(5, captor.getValue().getBoostWeight());
    }

    @Test
    void addCollectionProduct_publishesReindex() {
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        Product p = makeProduct(PRODUCT_ID, COMPANY_ID);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(p));
        when(collectionProductRepository.existsByCollectionIdAndProductId(any(), any())).thenReturn(false);
        when(productRepository.findAllByIdInAndCompanyId(any(), eq(COMPANY_ID))).thenReturn(List.of(p));
        when(collectionProductRepository.save(any())).thenAnswer(inv -> {
            CollectionProduct cp = inv.getArgument(0);
            cp.setId(TestIds.uuid(99));
            return cp;
        });

        AddCollectionProductRequest req = new AddCollectionProductRequest();
        req.setProductId(PRODUCT_ID);

        service.addCollectionProduct(COMPANY_ID, COLL_ID, OWNER_ID, req);

        verify(eventPublisher).publishEvent(any(ProductIndexEvent.class));
    }

    // ─── updateCollectionProduct ──────────────────────────────────────────────

    @Test
    void updateCollectionProduct_productNotInCollection_throwsResourceNotFound() {
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));
        when(collectionProductRepository.findByCollectionIdAndProductId(COLL_ID, PRODUCT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateCollectionProduct(COMPANY_ID, COLL_ID, PRODUCT_ID, OWNER_ID,
                        new UpdateCollectionProductRequest()));
    }

    @Test
    void updateCollectionProduct_updatesBoostAndPinnedRank() {
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        Product p = makeProduct(PRODUCT_ID, COMPANY_ID);
        CollectionProduct cp = makeCollectionProduct(TestIds.uuid(20), COLL_ID, p);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));
        when(collectionProductRepository.findByCollectionIdAndProductId(COLL_ID, PRODUCT_ID))
                .thenReturn(Optional.of(cp));
        when(collectionProductRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.findAllByIdInAndCompanyId(any(), any())).thenReturn(List.of(p));

        UpdateCollectionProductRequest req = new UpdateCollectionProductRequest();
        req.setBoostWeight(8);
        req.setPinnedRank(3);

        service.updateCollectionProduct(COMPANY_ID, COLL_ID, PRODUCT_ID, OWNER_ID, req);

        ArgumentCaptor<CollectionProduct> captor = ArgumentCaptor.forClass(CollectionProduct.class);
        verify(collectionProductRepository).save(captor.capture());
        assertEquals(8, captor.getValue().getBoostWeight());
        assertEquals(3, captor.getValue().getPinnedRank());
    }

    // ─── removeCollectionProduct ─────────────────────────────────────────────

    @Test
    void removeCollectionProduct_productNotInCollection_throwsResourceNotFound() {
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));
        when(collectionProductRepository.findByCollectionIdAndProductId(COLL_ID, PRODUCT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.removeCollectionProduct(COMPANY_ID, COLL_ID, PRODUCT_ID, OWNER_ID));
    }

    @Test
    void removeCollectionProduct_deletesRowAndReindexes() {
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        Product p = makeProduct(PRODUCT_ID, COMPANY_ID);
        CollectionProduct cp = makeCollectionProduct(TestIds.uuid(20), COLL_ID, p);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));
        when(collectionProductRepository.findByCollectionIdAndProductId(COLL_ID, PRODUCT_ID))
                .thenReturn(Optional.of(cp));
        when(productRepository.findAllByIdInAndCompanyId(any(), any())).thenReturn(List.of(p));

        service.removeCollectionProduct(COMPANY_ID, COLL_ID, PRODUCT_ID, OWNER_ID);

        verify(collectionProductRepository).delete(cp);
        verify(eventPublisher).publishEvent(any(ProductIndexEvent.class));
    }

    // ─── refreshCollection ───────────────────────────────────────────────────

    @Test
    void refreshCollection_staticCollection_throwsBadRequest() {
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));

        assertThrows(BadRequestException.class,
                () -> service.refreshCollection(COMPANY_ID, COLL_ID, OWNER_ID));
    }

    @Test
    void refreshCollection_dynamicCollection_materialises() {
        Collection c = makeCollection(COLL_ID, CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        c.setRulesJson("{\"tagsAnyOf\":[\"sale\"]}");
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(collectionProductRepository.findAllByCollectionIdAndSource(any(), any()))
                .thenReturn(List.of());

        service.refreshCollection(COMPANY_ID, COLL_ID, OWNER_ID);

        verify(productRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class));
        verify(collectionRepository).save(c);
    }

    // ─── materialiseDynamicMembers ────────────────────────────────────────────

    @Test
    void materialise_setsLastMaterialisedAt() {
        Collection c = makeCollection(COLL_ID, CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        c.setRulesJson("{\"tagsAnyOf\":[\"sale\"]}");
        when(productRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(collectionProductRepository.findAllByCollectionIdAndSource(any(), eq(CollectionMembershipSource.AUTO)))
                .thenReturn(List.of());

        service.materialiseDynamicMembers(c);

        assertNotNull(c.getLastMaterialisedAt());
    }

    @Test
    void materialise_matchingProduct_addsAutoRow() {
        Product p = makeProduct(PRODUCT_ID, COMPANY_ID);
        p.setTags("summer, sale");

        Collection c = makeCollection(COLL_ID, CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        c.setRulesJson("{\"tagsAnyOf\":[\"sale\"]}");
        when(productRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));
        when(collectionProductRepository.findAllByCollectionIdAndSource(any(), eq(CollectionMembershipSource.AUTO)))
                .thenReturn(List.of());
        when(collectionProductRepository.existsByCollectionIdAndProductId(any(), eq(PRODUCT_ID)))
                .thenReturn(false);
        when(productRepository.findAllByIdInAndCompanyId(any(), any())).thenReturn(List.of(p));

        service.materialiseDynamicMembers(c);

        ArgumentCaptor<CollectionProduct> captor = ArgumentCaptor.forClass(CollectionProduct.class);
        verify(collectionProductRepository).save(captor.capture());
        assertEquals(CollectionMembershipSource.AUTO, captor.getValue().getSource());
    }

    @Test
    void materialise_staleAutoRow_removed() {
        // A product no longer matches — it should be removed from AUTO membership
        Product stale = makeProduct(PRODUCT_ID, COMPANY_ID);
        stale.setTags("other-tag");

        CollectionProduct staleRow = makeCollectionProduct(TestIds.uuid(30), COLL_ID, stale);
        staleRow.setSource(CollectionMembershipSource.AUTO);

        Collection c = makeCollection(COLL_ID, CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        c.setRulesJson("{\"tagsAnyOf\":[\"sale\"]}");
        when(productRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stale)));
        when(collectionProductRepository.findAllByCollectionIdAndSource(COLL_ID, CollectionMembershipSource.AUTO))
                .thenReturn(List.of(staleRow));
        when(collectionProductRepository.findByCollectionIdAndProductId(COLL_ID, PRODUCT_ID))
                .thenReturn(Optional.of(staleRow));
        when(productRepository.findAllByIdInAndCompanyId(any(), any())).thenReturn(List.of(stale));

        service.materialiseDynamicMembers(c);

        verify(collectionProductRepository).delete(staleRow);
    }

    @Test
    void materialise_manualRowExists_doesNotAddAutoRow() {
        // Product matches but a MANUAL row already exists — AUTO row must not shadow it
        Product p = makeProduct(PRODUCT_ID, COMPANY_ID);
        p.setTags("sale");

        Collection c = makeCollection(COLL_ID, CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        c.setRulesJson("{\"tagsAnyOf\":[\"sale\"]}");
        when(productRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));
        when(collectionProductRepository.findAllByCollectionIdAndSource(any(), eq(CollectionMembershipSource.AUTO)))
                .thenReturn(List.of());
        // A MANUAL row already exists
        when(collectionProductRepository.existsByCollectionIdAndProductId(COLL_ID, PRODUCT_ID))
                .thenReturn(true);

        service.materialiseDynamicMembers(c);

        verify(collectionProductRepository, never()).save(any());
    }

    @Test
    void materialise_categoryMatch_addsAutoRow() {
        Product p = makeProduct(PRODUCT_ID, COMPANY_ID);
        p.setCategory("Apparel");

        Collection c = makeCollection(COLL_ID, CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        c.setRulesJson("{\"categoriesAnyOf\":[\"apparel\"]}");
        when(productRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));
        when(collectionProductRepository.findAllByCollectionIdAndSource(any(), eq(CollectionMembershipSource.AUTO)))
                .thenReturn(List.of());
        when(collectionProductRepository.existsByCollectionIdAndProductId(any(), any())).thenReturn(false);
        when(productRepository.findAllByIdInAndCompanyId(any(), any())).thenReturn(List.of(p));

        service.materialiseDynamicMembers(c);

        verify(collectionProductRepository).save(any(CollectionProduct.class));
    }

    @Test
    void materialise_brandMismatch_doesNotAddAutoRow() {
        Product p = makeProduct(PRODUCT_ID, COMPANY_ID);
        p.setBrand("OtherBrand");

        Collection c = makeCollection(COLL_ID, CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        c.setRulesJson("{\"brandsAnyOf\":[\"targetbrand\"]}");
        when(productRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));
        when(collectionProductRepository.findAllByCollectionIdAndSource(any(), eq(CollectionMembershipSource.AUTO)))
                .thenReturn(List.of());

        service.materialiseDynamicMembers(c);

        verify(collectionProductRepository, never()).save(any());
    }

    @Test
    void materialise_publishesReindexForAffectedProducts() {
        Product p = makeProduct(PRODUCT_ID, COMPANY_ID);
        p.setTags("sale");

        Collection c = makeCollection(COLL_ID, CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        c.setRulesJson("{\"tagsAnyOf\":[\"sale\"]}");
        when(productRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));
        when(collectionProductRepository.findAllByCollectionIdAndSource(any(), eq(CollectionMembershipSource.AUTO)))
                .thenReturn(List.of());
        when(collectionProductRepository.existsByCollectionIdAndProductId(any(), any())).thenReturn(false);
        when(productRepository.findAllByIdInAndCompanyId(any(), any())).thenReturn(List.of(p));

        service.materialiseDynamicMembers(c);

        verify(eventPublisher).publishEvent(any(ProductIndexEvent.class));
    }

    // ─── listFeaturedForMarketplace ───────────────────────────────────────────

    @Test
    void listFeaturedForMarketplace_marketplaceNotFound_throwsResourceNotFound() {
        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.listFeaturedForMarketplace(MARKETPLACE_ID));
    }

    @Test
    void listFeaturedForMarketplace_delegatesToCacheAndRepository() {
        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(true);
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findFeaturedForMarketplace(MARKETPLACE_ID)).thenReturn(List.of(c));
        // Make cache invoke the supplier directly
        when(singleFlightCache.getOrLoad(anyString(), anyLong(), any(),
                any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());

        List<CollectionResponse> result = service.listFeaturedForMarketplace(MARKETPLACE_ID);

        assertEquals(1, result.size());
        assertEquals(COLL_ID, result.get(0).id());
    }

    // ─── listFeaturedForVendor ────────────────────────────────────────────────

    @Test
    void listFeaturedForVendor_marketplaceNotFound_throwsResourceNotFound() {
        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.listFeaturedForVendor(MARKETPLACE_ID, COMPANY_ID));
    }

    @Test
    void listFeaturedForVendor_returnsVendorCollections() {
        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(true);
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findFeaturedForVendor(COMPANY_ID)).thenReturn(List.of(c));
        when(singleFlightCache.getOrLoad(anyString(), anyLong(), any(),
                any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());

        List<CollectionResponse> result = service.listFeaturedForVendor(MARKETPLACE_ID, COMPANY_ID);

        assertEquals(1, result.size());
    }

    // ─── getCollectionBySlug ──────────────────────────────────────────────────

    @Test
    void getCollectionBySlug_marketplaceNotFound_throwsResourceNotFound() {
        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.getCollectionBySlug(MARKETPLACE_ID, "some-slug"));
    }

    @Test
    void getCollectionBySlug_noVendorHasSlug_throwsResourceNotFound() {
        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(true);
        when(productRepository.findMarketplaceListed(MARKETPLACE_ID)).thenReturn(List.of());
        when(singleFlightCache.getOrLoad(anyString(), anyLong(), any(), any(Class.class)))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getCollectionBySlug(MARKETPLACE_ID, "missing-slug"));
    }

    // ─── listCollectionProducts ───────────────────────────────────────────────

    @Test
    void listCollectionProducts_companyNotFound_throwsResourceNotFound() {
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.listCollectionProducts(COMPANY_ID, COLL_ID, 0, 20));
    }

    @Test
    void listCollectionProducts_collectionNotFound_throwsResourceNotFound() {
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.listCollectionProducts(COMPANY_ID, COLL_ID, 0, 20));
    }

    @Test
    void listCollectionProducts_returnsPagedResponses() {
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        Product p = makeProduct(PRODUCT_ID, COMPANY_ID);
        CollectionProduct cp = makeCollectionProduct(TestIds.uuid(20), COLL_ID, p);
        cp.setCollection(c);

        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));
        when(collectionProductRepository.findAllByCollectionIdRanked(eq(COLL_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cp)));

        var result = service.listCollectionProducts(COMPANY_ID, COLL_ID, 0, 20);

        assertEquals(1, result.getItems().size());
        assertEquals(PRODUCT_ID, result.getItems().get(0).productId());
    }

    // ─── listMarketplaceCollectionProducts ────────────────────────────────────

    @Test
    void listMarketplaceCollectionProducts_marketplaceNotFound_throwsResourceNotFound() {
        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.listMarketplaceCollectionProducts(MARKETPLACE_ID, "some-slug", 0, 20));
    }

    @Test
    void listMarketplaceCollectionProducts_noVendorHasSlug_throwsResourceNotFound() {
        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(true);
        when(productRepository.findMarketplaceListed(MARKETPLACE_ID)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service.listMarketplaceCollectionProducts(MARKETPLACE_ID, "missing-slug", 0, 20));
    }

    @Test
    void listMarketplaceCollectionProducts_returnsOnlyActiveMarketplaceListedProducts() {
        // Set up vendor product so findActiveBySlugForMarketplace resolves the collection
        Product vendorProduct = makeProduct(TestIds.uuid(7), COMPANY_ID);
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        c.setSlug("test-slug");

        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(true);
        when(productRepository.findMarketplaceListed(MARKETPLACE_ID)).thenReturn(List.of(vendorProduct));
        when(collectionRepository.findBySlugAndCompanyId("test-slug", COMPANY_ID)).thenReturn(Optional.of(c));

        // visible product: ACTIVE, marketplace-listed, correct marketplaceId
        Product visible = makeProduct(PRODUCT_ID, COMPANY_ID);
        visible.setMarketplaceId(MARKETPLACE_ID);
        visible.setMarketplaceListed(true);
        CollectionProduct visibleCp = makeCollectionProduct(TestIds.uuid(21), COLL_ID, visible);
        visibleCp.setCollection(c);

        // The repository query itself restricts to ACTIVE, marketplace-listed products for this
        // marketplace, so the mock only returns the visible row.
        when(collectionProductRepository.findVisibleByCollectionIdRanked(eq(COLL_ID), eq(MARKETPLACE_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(visibleCp)));

        var result = service.listMarketplaceCollectionProducts(MARKETPLACE_ID, "test-slug", 0, 20);

        assertEquals(1, result.getItems().size());
        assertEquals(PRODUCT_ID, result.getItems().get(0).productId());
    }

    // ─── getCollectionBySlug — happy path ─────────────────────────────────────

    @Test
    void getCollectionBySlug_vendorHasActiveCollection_returnsResponse() {
        Product vendorProduct = makeProduct(TestIds.uuid(7), COMPANY_ID);
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        c.setSlug("my-slug");

        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(true);
        when(productRepository.findMarketplaceListed(MARKETPLACE_ID)).thenReturn(List.of(vendorProduct));
        when(collectionRepository.findBySlugAndCompanyId("my-slug", COMPANY_ID)).thenReturn(Optional.of(c));
        when(singleFlightCache.getOrLoad(anyString(), anyLong(), any(), any(Class.class)))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());

        var result = service.getCollectionBySlug(MARKETPLACE_ID, "my-slug");

        assertEquals(COLL_ID, result.id());
    }

    // ─── materialiseDynamicMembers — non-DYNAMIC guard ────────────────────────

    @Test
    void materialise_nonDynamic_returnsEarlyWithoutQuery() {
        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);

        service.materialiseDynamicMembers(c);

        verifyNoInteractions(productRepository);
    }

    // ─── evictCollectionCaches — Redis failure is swallowed ───────────────────

    @Test
    void evictCollectionCaches_redisFailure_doesNotPropagate() {
        doThrow(new RuntimeException("Redis down")).when(singleFlightCache).evict(anyString());

        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));
        when(collectionProductRepository.findAllByCollectionId(COLL_ID)).thenReturn(List.of());

        // deleteCollection calls evictAfterCommit which, outside a real transaction,
        // runs the eviction immediately — Redis failure must be swallowed
        assertDoesNotThrow(() -> service.deleteCollection(COMPANY_ID, COLL_ID, OWNER_ID));
    }

    // ─── toResponse — countByCollectionId Redis fallback ─────────────────────

    @Test
    void toResponse_countByCollectionIdThrows_defaultsToZero() {
        doThrow(new RuntimeException("Redis timeout"))
                .when(collectionProductRepository).countByCollectionId(COLL_ID);

        Collection c = makeCollection(COLL_ID, CollectionType.STATIC, CollectionStatus.ACTIVE);
        when(collectionRepository.findByIdAndCompanyId(COLL_ID, COMPANY_ID)).thenReturn(Optional.of(c));

        var result = service.getCollection(COMPANY_ID, COLL_ID);

        assertEquals(0L, result.productCount());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Company makeCompany(UUID id) {
        Company c = new Company();
        c.setId(id);
        return c;
    }

    private Collection makeCollection(UUID id, CollectionType type, CollectionStatus status) {
        Company company = makeCompany(COMPANY_ID);
        Collection c = new Collection();
        c.setId(id);
        c.setCompany(company);
        c.setName("Test Collection " + id);
        c.setSlug("test-slug");
        c.setType(type);
        c.setStatus(status);
        return c;
    }

    private Product makeProduct(UUID id, UUID companyId) {
        Company company = makeCompany(companyId);
        Product p = new Product();
        p.setId(id);
        p.setCompany(company);
        p.setName("Product " + id);
        p.setSku("SKU-" + id);
        p.setPrice(BigDecimal.TEN);
        p.setCurrency("USD");
        p.setStatus(ProductStatus.ACTIVE);
        return p;
    }

    private CollectionProduct makeCollectionProduct(UUID id, UUID collectionId, Product product) {
        CollectionProduct cp = new CollectionProduct();
        cp.setId(id);
        Collection c = new Collection();
        c.setId(collectionId);
        cp.setCollection(c);
        cp.setProduct(product);
        cp.setSource(CollectionMembershipSource.MANUAL);
        return cp;
    }

    private CreateCollectionRequest makeCreateRequest(String name, String slug,
                                                      CollectionType type, String rulesJson) {
        CreateCollectionRequest req = new CreateCollectionRequest();
        req.setName(name);
        req.setSlug(slug);
        req.setType(type);
        req.setRulesJson(rulesJson);
        return req;
    }
}
