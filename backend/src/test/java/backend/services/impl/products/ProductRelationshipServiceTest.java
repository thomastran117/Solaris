package backend.services.impl.products;

import backend.dtos.requests.product.AddProductRelationshipRequest;
import backend.dtos.responses.product.ProductRelationshipResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductRelationship;
import backend.models.enums.CompanyCapability;
import backend.models.enums.ProductRelationshipType;
import backend.repositories.BundleRepository;
import backend.repositories.CollectionProductRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.ProductAttributeRepository;
import backend.repositories.ProductChangeLogRepository;
import backend.repositories.ProductImageRepository;
import backend.repositories.ProductOptionRepository;
import backend.repositories.ProductRelationshipRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.PromotionRuleRepository;
import backend.services.impl.SingleFlightCache;
import backend.services.impl.pricing.ActivePromotionLookupService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.products.ProductChangeLogger;
import backend.testutil.TestIds;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductRelationshipServiceTest {

    private static final UUID COMPANY_ID  = TestIds.uuid(1);
    private static final UUID OWNER_ID    = TestIds.uuid(2);
    private static final UUID PRODUCT_ID  = TestIds.uuid(3);
    private static final UUID TARGET_ID   = TestIds.uuid(4);
    private static final UUID OTHER_CO_ID = TestIds.uuid(9);

    private ProductRepository productRepository;
    private CompanyRepository companyRepository;
    private ProductRelationshipRepository productRelationshipRepository;
    private ActivePromotionLookupService activePromotionLookupService;
    private SingleFlightCache singleFlightCache;
    private CompanyAccessService companyAccessService;

    private ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        productRepository               = mock(ProductRepository.class);
        companyRepository               = mock(CompanyRepository.class);
        productRelationshipRepository   = mock(ProductRelationshipRepository.class);
        activePromotionLookupService    = mock(ActivePromotionLookupService.class);
        singleFlightCache               = mock(SingleFlightCache.class);
        companyAccessService            = mock(CompanyAccessService.class);

        service = new ProductServiceImpl(
                productRepository,
                companyRepository,
                mock(ProductImageRepository.class),
                mock(ProductOptionRepository.class),
                mock(ProductVariantRepository.class),
                mock(ProductAttributeRepository.class),
                mock(ProductReviewRepository.class),
                mock(BundleRepository.class),
                mock(CollectionProductRepository.class),
                mock(PromotionRuleRepository.class),
                mock(MarketplaceProfileRepository.class),
                mock(MarketplaceVendorRepository.class),
                mock(ApplicationEventPublisher.class),
                mock(ElasticsearchOperations.class),
                singleFlightCache,
                activePromotionLookupService,
                mock(ProductChangeLogger.class),
                mock(ProductChangeLogRepository.class),
                mock(InventoryAdjustmentRepository.class),
                productRelationshipRepository,
                mock(backend.repositories.ProductSimilarityRepository.class),
                companyAccessService,
                300L, 60L);

        when(companyRepository.existsById(COMPANY_ID)).thenReturn(true);
        when(activePromotionLookupService.findForProducts(any())).thenReturn(Map.of());

        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(Class.class));
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(TypeReference.class));
    }

    // ─── getProductRelationships ──────────────────────────────────────────────

    @Test
    void getRelationships_noTypeFilter_returnsAll() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        ProductRelationship rel = makeRelationship(PRODUCT_ID, TARGET_ID, ProductRelationshipType.UPGRADE);
        when(productRelationshipRepository.findAllBySourceProductIdAndSourceProductCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(List.of(rel));

        List<ProductRelationshipResponse> result = service.getProductRelationships(COMPANY_ID, PRODUCT_ID, null);

        assertEquals(1, result.size());
        assertEquals(TARGET_ID, result.get(0).targetProductId());
        assertEquals(ProductRelationshipType.UPGRADE, result.get(0).type());
    }

    @Test
    void getRelationships_withTypeFilter_returnsFiltered() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        ProductRelationship rel = makeRelationship(PRODUCT_ID, TARGET_ID, ProductRelationshipType.ACCESSORY);
        when(productRelationshipRepository.findAllBySourceProductIdAndTypeAndSourceProductCompanyId(
                PRODUCT_ID, ProductRelationshipType.ACCESSORY, COMPANY_ID))
                .thenReturn(List.of(rel));

        List<ProductRelationshipResponse> result = service.getProductRelationships(COMPANY_ID, PRODUCT_ID, ProductRelationshipType.ACCESSORY);

        assertEquals(1, result.size());
        assertEquals(ProductRelationshipType.ACCESSORY, result.get(0).type());
        verify(productRelationshipRepository, never())
                .findAllBySourceProductIdAndSourceProductCompanyId(any(), any());
    }

    @Test
    void getRelationships_productNotFound_throwsNotFoundException() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getProductRelationships(COMPANY_ID, PRODUCT_ID, null));
    }

    // ─── addProductRelationship ───────────────────────────────────────────────

    @Test
    void addRelationship_happyPath_persistsAndReturnsResponse() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(productRepository.findByIdAndCompanyId(TARGET_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct(TARGET_ID)));
        when(productRelationshipRepository.existsBySourceProductIdAndTargetProductIdAndType(
                PRODUCT_ID, TARGET_ID, ProductRelationshipType.UPGRADE)).thenReturn(false);
        when(productRelationshipRepository.save(any(ProductRelationship.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AddProductRelationshipRequest req = new AddProductRelationshipRequest();
        req.setTargetProductId(TARGET_ID);
        req.setType(ProductRelationshipType.UPGRADE);
        req.setNote("Premium version");

        ProductRelationshipResponse result = service.addProductRelationship(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        verify(productRelationshipRepository).save(any(ProductRelationship.class));
        assertEquals(TARGET_ID, result.targetProductId());
        assertEquals(ProductRelationshipType.UPGRADE, result.type());
        assertEquals("Premium version", result.note());
    }

    @Test
    void addRelationship_selfReference_throwsBadRequest() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct(PRODUCT_ID)));

        AddProductRelationshipRequest req = new AddProductRelationshipRequest();
        req.setTargetProductId(PRODUCT_ID);
        req.setType(ProductRelationshipType.ALTERNATIVE);

        assertThrows(BadRequestException.class,
                () -> service.addProductRelationship(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
        verify(productRelationshipRepository, never()).save(any());
    }

    @Test
    void addRelationship_targetProductNotInCompany_throwsNotFoundException() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(productRepository.findByIdAndCompanyId(TARGET_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        AddProductRelationshipRequest req = new AddProductRelationshipRequest();
        req.setTargetProductId(TARGET_ID);
        req.setType(ProductRelationshipType.UPGRADE);

        assertThrows(ResourceNotFoundException.class,
                () -> service.addProductRelationship(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
        verify(productRelationshipRepository, never()).save(any());
    }

    @Test
    void addRelationship_duplicate_throwsConflict() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(productRepository.findByIdAndCompanyId(TARGET_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct(TARGET_ID)));
        when(productRelationshipRepository.existsBySourceProductIdAndTargetProductIdAndType(
                PRODUCT_ID, TARGET_ID, ProductRelationshipType.REPLACEMENT)).thenReturn(true);

        AddProductRelationshipRequest req = new AddProductRelationshipRequest();
        req.setTargetProductId(TARGET_ID);
        req.setType(ProductRelationshipType.REPLACEMENT);

        assertThrows(ConflictException.class,
                () -> service.addProductRelationship(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
        verify(productRelationshipRepository, never()).save(any());
    }

    // ─── removeProductRelationship ────────────────────────────────────────────

    @Test
    void removeRelationship_happyPath_deletesRow() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(productRelationshipRepository.existsBySourceProductIdAndTargetProductIdAndType(
                PRODUCT_ID, TARGET_ID, ProductRelationshipType.ACCESSORY)).thenReturn(true);

        service.removeProductRelationship(COMPANY_ID, PRODUCT_ID, TARGET_ID, ProductRelationshipType.ACCESSORY, OWNER_ID);

        verify(productRelationshipRepository).deleteBySourceProductIdAndTargetProductIdAndType(
                PRODUCT_ID, TARGET_ID, ProductRelationshipType.ACCESSORY);
    }

    @Test
    void removeRelationship_notFound_throwsNotFoundException() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(productRelationshipRepository.existsBySourceProductIdAndTargetProductIdAndType(
                PRODUCT_ID, TARGET_ID, ProductRelationshipType.UPGRADE)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.removeProductRelationship(COMPANY_ID, PRODUCT_ID, TARGET_ID, ProductRelationshipType.UPGRADE, OWNER_ID));
        verify(productRelationshipRepository, never()).deleteBySourceProductIdAndTargetProductIdAndType(any(), any(), any());
    }

    @Test
    void removeRelationship_sourceProductNotFound_throwsNotFoundException() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.removeProductRelationship(COMPANY_ID, PRODUCT_ID, TARGET_ID, ProductRelationshipType.UPGRADE, OWNER_ID));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static Product makeProduct(UUID id) {
        Company company = new Company();
        company.setId(COMPANY_ID);

        Product p = new Product();
        p.setId(id);
        p.setName("Product-" + id);
        p.setSku("SKU-" + id);
        p.setPrice(BigDecimal.TEN);
        p.setCurrency("USD");
        p.setStatus(backend.models.enums.ProductStatus.ACTIVE);
        p.setCompany(company);
        return p;
    }

    private static ProductRelationship makeRelationship(UUID sourceId, UUID targetId, ProductRelationshipType type) {
        Product source = makeProduct(sourceId);
        Product target = makeProduct(targetId);

        ProductRelationship rel = new ProductRelationship();
        rel.setId(TestIds.uuid(50));
        rel.setSourceProduct(source);
        rel.setTargetProduct(target);
        rel.setType(type);
        return rel;
    }
}
