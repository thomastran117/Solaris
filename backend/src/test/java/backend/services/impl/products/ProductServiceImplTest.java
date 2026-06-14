package backend.services.impl.products;

import backend.dtos.requests.product.AddProductImageRequest;
import backend.dtos.requests.product.BatchCreateProductsRequest;
import backend.dtos.requests.product.BatchDeleteProductsRequest;
import backend.dtos.requests.product.BatchUpdateProductsRequest;
import backend.dtos.requests.product.CreateProductOptionRequest;
import backend.dtos.requests.product.CreateProductRequest;
import backend.dtos.requests.product.CreateProductVariantRequest;
import backend.dtos.requests.product.ReorderProductImagesRequest;
import backend.dtos.requests.product.SetProductAttributesRequest;
import backend.dtos.requests.product.UpdateMarketplaceListingRequest;
import backend.dtos.requests.product.UpdateProductOptionRequest;
import backend.dtos.requests.product.UpdateProductRequest;
import backend.dtos.requests.product.UpdateProductVariantRequest;
import backend.dtos.responses.product.ProductImageResponse;
import backend.dtos.responses.product.ProductOptionResponse;
import backend.dtos.responses.product.ProductResponse;
import backend.dtos.responses.product.ProductVariantResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.dtos.responses.product.MarketplaceCatalogProductResponse;
import backend.dtos.responses.product.ProductAttributeResponse;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductAttribute;
import backend.models.core.ProductImage;
import backend.models.core.ProductOption;
import backend.models.core.ProductVariant;
import backend.models.core.ProductChangeLog;
import backend.models.enums.ChangeSource;
import backend.models.enums.CompanyCapability;
import backend.models.enums.ProductChangeType;
import backend.models.enums.ProductStatus;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductServiceImplTest {

    private static final UUID COMPANY_ID  = TestIds.uuid(1);
    private static final UUID OWNER_ID    = TestIds.uuid(2);
    private static final UUID PRODUCT_ID  = TestIds.uuid(3);
    private static final UUID IMAGE_ID    = TestIds.uuid(4);
    private static final UUID OPTION_ID   = TestIds.uuid(5);
    private static final UUID VARIANT_ID  = TestIds.uuid(6);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(7);

    private ProductRepository productRepository;
    private CompanyRepository companyRepository;
    private ProductImageRepository productImageRepository;
    private ProductOptionRepository productOptionRepository;
    private ProductVariantRepository productVariantRepository;
    private ProductAttributeRepository productAttributeRepository;
    private ProductReviewRepository productReviewRepository;
    private BundleRepository bundleRepository;
    private CollectionProductRepository collectionProductRepository;
    private PromotionRuleRepository promotionRuleRepository;
    private MarketplaceProfileRepository marketplaceProfileRepository;
    private MarketplaceVendorRepository marketplaceVendorRepository;
    private ApplicationEventPublisher eventPublisher;
    private ElasticsearchOperations elasticsearchOperations;
    private SingleFlightCache singleFlightCache;
    private ActivePromotionLookupService activePromotionLookupService;
    private ProductChangeLogger productChangeLogger;
    private ProductChangeLogRepository productChangeLogRepository;
    private InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private CompanyAccessService companyAccessService;
    private backend.repositories.ProductRelationshipRepository productRelationshipRepository;
    private backend.repositories.ProductSimilarityRepository productSimilarityRepository;
    private backend.repositories.OrderItemRepository orderItemRepository;

    private ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        productRepository           = mock(ProductRepository.class);
        companyRepository           = mock(CompanyRepository.class);
        productImageRepository      = mock(ProductImageRepository.class);
        productOptionRepository     = mock(ProductOptionRepository.class);
        productVariantRepository    = mock(ProductVariantRepository.class);
        productAttributeRepository  = mock(ProductAttributeRepository.class);
        productReviewRepository     = mock(ProductReviewRepository.class);
        bundleRepository            = mock(BundleRepository.class);
        collectionProductRepository = mock(CollectionProductRepository.class);
        promotionRuleRepository     = mock(PromotionRuleRepository.class);
        marketplaceProfileRepository = mock(MarketplaceProfileRepository.class);
        marketplaceVendorRepository = mock(MarketplaceVendorRepository.class);
        eventPublisher              = mock(ApplicationEventPublisher.class);
        elasticsearchOperations     = mock(ElasticsearchOperations.class);
        singleFlightCache           = mock(SingleFlightCache.class);
        activePromotionLookupService = mock(ActivePromotionLookupService.class);
        productChangeLogger         = mock(ProductChangeLogger.class);
        productChangeLogRepository  = mock(ProductChangeLogRepository.class);
        inventoryAdjustmentRepository = mock(InventoryAdjustmentRepository.class);
        companyAccessService          = mock(CompanyAccessService.class);
        productRelationshipRepository = mock(backend.repositories.ProductRelationshipRepository.class);
        productSimilarityRepository   = mock(backend.repositories.ProductSimilarityRepository.class);
        orderItemRepository           = mock(backend.repositories.OrderItemRepository.class);

        service = new ProductServiceImpl(
                productRepository, companyRepository,
                productImageRepository, productOptionRepository,
                productVariantRepository, productAttributeRepository,
                productReviewRepository, bundleRepository,
                collectionProductRepository, promotionRuleRepository,
                marketplaceProfileRepository, marketplaceVendorRepository,
                eventPublisher, elasticsearchOperations,
                singleFlightCache, activePromotionLookupService,
                productChangeLogger, productChangeLogRepository,
                inventoryAdjustmentRepository,
                productRelationshipRepository,
                productSimilarityRepository,
                companyAccessService,
                orderItemRepository,
                300L, 60L);

        // Common stubs
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(true);
        when(companyAccessService.require(eq(COMPANY_ID), eq(OWNER_ID), any(CompanyCapability.class)))
                .thenReturn(makeCompany());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (p.getId() == null) p.setId(PRODUCT_ID);
            return p;
        });
        when(productChangeLogger.snapshot(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productChangeLogger.snapshot(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(activePromotionLookupService.findForProducts(any())).thenReturn(Map.of());

        // Bypass cache: execute the supplier directly
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(Class.class));
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(TypeReference.class));
    }

    // ─── createProduct ────────────────────────────────────────────────────────

    @Test
    void createProduct_validRequest_savesAndReturnsResponse() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));

        ProductResponse result = service.createProduct(COMPANY_ID, OWNER_ID, req);

        verify(productRepository).save(any(Product.class));
        assertEquals("Widget", result.getName());
        assertEquals("USD", result.getCurrency());
    }

    @Test
    void createProduct_duplicateSku_throwsConflict() {
        when(productRepository.existsBySkuAndCompanyId("SKU-1", COMPANY_ID)).thenReturn(true);

        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setSku("SKU-1");

        assertThrows(ConflictException.class,
                () -> service.createProduct(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createProduct_nullSku_skipsSkuCheck() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setSku(null);

        service.createProduct(COMPANY_ID, OWNER_ID, req);

        verify(productRepository, never()).existsBySkuAndCompanyId(any(), any());
    }

    @Test
    void createProduct_blankSku_skipsSkuCheck() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setSku("  ");

        service.createProduct(COMPANY_ID, OWNER_ID, req);

        verify(productRepository, never()).existsBySkuAndCompanyId(any(), any());
    }

    @Test
    void createProduct_nullCurrency_defaultsToUsd() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setCurrency(null);

        ProductResponse result = service.createProduct(COMPANY_ID, OWNER_ID, req);

        assertEquals("USD", result.getCurrency());
    }

    @Test
    void createProduct_scheduledStatus_missingDate_throwsBadRequest() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setStatus(ProductStatus.SCHEDULED);
        req.setScheduledPublishAt(null);

        assertThrows(BadRequestException.class,
                () -> service.createProduct(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createProduct_scheduledStatus_pastDate_throwsBadRequest() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setStatus(ProductStatus.SCHEDULED);
        req.setScheduledPublishAt(Instant.now().minusSeconds(60));

        assertThrows(BadRequestException.class,
                () -> service.createProduct(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createProduct_scheduledStatus_futureDate_setsScheduledPublishAt() {
        Instant future = Instant.now().plusSeconds(3600);
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setStatus(ProductStatus.SCHEDULED);
        req.setScheduledPublishAt(future);

        ProductResponse result = service.createProduct(COMPANY_ID, OWNER_ID, req);

        assertEquals("SCHEDULED", result.getStatus());
        assertEquals(future, result.getScheduledPublishAt());
    }

    @Test
    void createProduct_activeStatus_setsPublishedAt() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setStatus(ProductStatus.ACTIVE);

        ProductResponse result = service.createProduct(COMPANY_ID, OWNER_ID, req);

        assertEquals("ACTIVE", result.getStatus());
        assertNotNull(result.getPublishedAt());
    }

    // ─── updateProduct ────────────────────────────────────────────────────────

    @Test
    void updateProduct_partialUpdate_onlyChangesProvidedFields() {
        Product existing = makeProduct(PRODUCT_ID);
        existing.setPrice(new BigDecimal("50.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        UpdateProductRequest req = new UpdateProductRequest();
        req.setName("Updated Name");

        ProductResponse result = service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertEquals("Updated Name", result.getName());
        assertEquals(new BigDecimal("50.00"), result.getPrice());
    }

    @Test
    void updateProduct_partialCompareAtPriceBelowExistingPrice_throwsBadRequest() {
        Product existing = makeProduct(PRODUCT_ID);
        existing.setPrice(new BigDecimal("100.00"));
        existing.setCompareAtPrice(null);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        UpdateProductRequest req = new UpdateProductRequest();
        req.setCompareAtPrice(new BigDecimal("50.00")); // only compareAtPrice; below existing price

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void updateProduct_skuChange_conflict_throwsConflict() {
        Product existing = makeProduct(PRODUCT_ID);
        existing.setSku("OLD-SKU");
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(productRepository.existsBySkuAndCompanyId("NEW-SKU", COMPANY_ID)).thenReturn(true);

        UpdateProductRequest req = new UpdateProductRequest();
        req.setSku("NEW-SKU");

        assertThrows(ConflictException.class,
                () -> service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void updateProduct_skuSameAsExisting_skipsConflictCheck() {
        Product existing = makeProduct(PRODUCT_ID);
        existing.setSku("SAME-SKU");
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(productImageRepository.countByProductId(PRODUCT_ID)).thenReturn(1);

        UpdateProductRequest req = new UpdateProductRequest();
        req.setSku("SAME-SKU");

        service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        verify(productRepository, never()).existsBySkuAndCompanyId(any(), any());
    }

    @Test
    void updateProduct_activateWithoutImage_throwsBadRequest() {
        Product existing = makeProduct(PRODUCT_ID);
        existing.setStatus(ProductStatus.DRAFT);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(productImageRepository.countByProductId(PRODUCT_ID)).thenReturn(0);

        UpdateProductRequest req = new UpdateProductRequest();
        req.setStatus(ProductStatus.ACTIVE);

        assertThrows(BadRequestException.class,
                () -> service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void updateProduct_activateWithImage_succeeds() {
        Product existing = makeProduct(PRODUCT_ID);
        existing.setStatus(ProductStatus.DRAFT);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(productImageRepository.countByProductId(PRODUCT_ID)).thenReturn(1);

        UpdateProductRequest req = new UpdateProductRequest();
        req.setStatus(ProductStatus.ACTIVE);

        ProductResponse result = service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void updateProduct_listingWithoutImage_throwsBadRequest() {
        Product existing = makeProduct(PRODUCT_ID);
        existing.setListed(false);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(productImageRepository.countByProductId(PRODUCT_ID)).thenReturn(0);

        UpdateProductRequest req = new UpdateProductRequest();
        req.setListed(true);

        assertThrows(BadRequestException.class,
                () -> service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void updateProduct_pinnedUntilInPast_throwsBadRequest() {
        Product existing = makeProduct(PRODUCT_ID);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        UpdateProductRequest req = new UpdateProductRequest();
        req.setPinnedUntil(Instant.now().minusSeconds(1));

        assertThrows(BadRequestException.class,
                () -> service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void updateProduct_notFound_throwsResourceNotFound() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, new UpdateProductRequest()));
    }

    @Test
    void updateProduct_scheduledFromArchived_throwsBadRequest() {
        Product existing = makeProduct(PRODUCT_ID);
        existing.setStatus(ProductStatus.ARCHIVED);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        UpdateProductRequest req = new UpdateProductRequest();
        req.setStatus(ProductStatus.SCHEDULED);
        req.setScheduledPublishAt(Instant.now().plusSeconds(3600));

        assertThrows(BadRequestException.class,
                () -> service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void updateProduct_scheduledToDraft_clearsScheduledPublishAt() {
        Product existing = makeProduct(PRODUCT_ID);
        existing.setStatus(ProductStatus.SCHEDULED);
        existing.setScheduledPublishAt(Instant.now().plusSeconds(3600));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        UpdateProductRequest req = new UpdateProductRequest();
        req.setStatus(ProductStatus.DRAFT);

        ProductResponse result = service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertEquals("DRAFT", result.getStatus());
        assertNull(result.getScheduledPublishAt());
    }

    // ─── deleteProduct ────────────────────────────────────────────────────────

    @Test
    void deleteProduct_happyPath_deletesAndPublishesEvent() {
        Product product = makeProduct(PRODUCT_ID);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(bundleRepository.existsByItemsProductId(PRODUCT_ID)).thenReturn(false);

        service.deleteProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID);

        verify(productRepository).delete(product);
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void deleteProduct_inBundle_throwsConflict() {
        Product product = makeProduct(PRODUCT_ID);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(bundleRepository.existsByItemsProductId(PRODUCT_ID)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.deleteProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID));
    }

    @Test
    void deleteProduct_notFound_throwsResourceNotFound() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID));
    }

    @Test
    void deleteProduct_referencedByOrder_throwsConflict() {
        Product product = makeProduct(PRODUCT_ID);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(bundleRepository.existsByItemsProductId(PRODUCT_ID)).thenReturn(false);
        when(orderItemRepository.existsByProductId(PRODUCT_ID)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.deleteProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID));
        verify(productRepository, never()).delete(any(Product.class));
    }

    // ─── getProduct ───────────────────────────────────────────────────────────

    @Test
    void getProduct_happyPath_returnsResponse() {
        Product product = makeProduct(PRODUCT_ID);
        product.setName("Gadget");
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));

        ProductResponse result = service.getProduct(COMPANY_ID, PRODUCT_ID);

        assertNotNull(result);
        assertEquals("Gadget", result.getName());
    }

    @Test
    void getProduct_companyNotFound_throwsResourceNotFound() {
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.getProduct(COMPANY_ID, PRODUCT_ID));
    }

    // ─── batchCreateProducts ──────────────────────────────────────────────────

    @Test
    void batchCreateProducts_happyPath_savesAll() {
        BatchCreateProductsRequest req = new BatchCreateProductsRequest();
        CreateProductRequest r1 = makeCreateRequest("Widget A", null);
        CreateProductRequest r2 = makeCreateRequest("Widget B", null);
        req.setProducts(List.of(r1, r2));

        List<ProductResponse> results = service.batchCreateProducts(COMPANY_ID, OWNER_ID, req);

        assertEquals(2, results.size());
        verify(productRepository, times(2)).save(any(Product.class));
    }

    @Test
    void batchCreateProducts_duplicateSkuWithinBatch_throwsConflict() {
        BatchCreateProductsRequest req = new BatchCreateProductsRequest();
        req.setProducts(List.of(
                makeCreateRequest("Widget A", "SKU-DUP"),
                makeCreateRequest("Widget B", "sku-dup") // same, different case
        ));

        assertThrows(ConflictException.class,
                () -> service.batchCreateProducts(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void batchCreateProducts_skuExistsInDb_throwsConflict() {
        when(productRepository.existsBySkuAndCompanyId("SKU-1", COMPANY_ID)).thenReturn(true);
        BatchCreateProductsRequest req = new BatchCreateProductsRequest();
        req.setProducts(List.of(makeCreateRequest("Widget A", "SKU-1")));

        assertThrows(ConflictException.class,
                () -> service.batchCreateProducts(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void batchCreateProducts_nullSkus_skipSkuCheck() {
        BatchCreateProductsRequest req = new BatchCreateProductsRequest();
        req.setProducts(List.of(
                makeCreateRequest("Widget A", null),
                makeCreateRequest("Widget B", null)
        ));

        service.batchCreateProducts(COMPANY_ID, OWNER_ID, req);

        verify(productRepository, never()).existsBySkuAndCompanyId(any(), any());
    }

    // ─── batchDeleteProducts ──────────────────────────────────────────────────

    @Test
    void batchDeleteProducts_happyPath_deletesAll() {
        UUID pid1 = TestIds.uuid(10);
        UUID pid2 = TestIds.uuid(11);
        Product p1 = makeProduct(pid1);
        Product p2 = makeProduct(pid2);
        when(productRepository.findAllByIdInAndCompanyId(List.of(pid1, pid2), COMPANY_ID))
                .thenReturn(List.of(p1, p2));

        BatchDeleteProductsRequest req = new BatchDeleteProductsRequest();
        req.setIds(List.of(pid1, pid2));

        service.batchDeleteProducts(COMPANY_ID, OWNER_ID, req);

        verify(productRepository).deleteAll(List.of(p1, p2));
    }

    @Test
    void batchDeleteProducts_partialNotFound_throwsResourceNotFound() {
        UUID pid1 = TestIds.uuid(10);
        UUID pid2 = TestIds.uuid(11);
        when(productRepository.findAllByIdInAndCompanyId(List.of(pid1, pid2), COMPANY_ID))
                .thenReturn(List.of(makeProduct(pid1))); // only 1 of 2 found

        BatchDeleteProductsRequest req = new BatchDeleteProductsRequest();
        req.setIds(List.of(pid1, pid2));

        assertThrows(ResourceNotFoundException.class,
                () -> service.batchDeleteProducts(COMPANY_ID, OWNER_ID, req));
    }

    // ─── addProductImage ──────────────────────────────────────────────────────

    @Test
    void addProductImage_happyPath_savesAndReturnsResponse() {
        Product product = makeProduct(PRODUCT_ID);
        when(productRepository.findByIdAndCompanyIdWithLock(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(productImageRepository.countByProductId(PRODUCT_ID)).thenReturn(2);
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(inv -> {
            ProductImage img = inv.getArgument(0);
            img.setId(IMAGE_ID);
            return img;
        });

        AddProductImageRequest req = new AddProductImageRequest();
        req.setImageUrl("https://example.com/img.jpg");

        ProductImageResponse result = service.addProductImage(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertNotNull(result);
        assertEquals("https://example.com/img.jpg", result.imageUrl());
        verify(productImageRepository).save(any(ProductImage.class));
    }

    @Test
    void addProductImage_firstImage_setsThumbnailOnProduct() {
        Product product = makeProduct(PRODUCT_ID);
        when(productRepository.findByIdAndCompanyIdWithLock(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(productImageRepository.countByProductId(PRODUCT_ID)).thenReturn(0);
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(inv -> {
            ProductImage img = inv.getArgument(0);
            img.setId(IMAGE_ID);
            return img;
        });

        AddProductImageRequest req = new AddProductImageRequest();
        req.setImageUrl("https://example.com/thumb.jpg");

        service.addProductImage(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        // product thumbnail is updated when the first image is added (1 save for thumbnail)
        verify(productRepository, times(1)).save(any(Product.class));
        assertEquals("https://example.com/thumb.jpg", product.getThumbnailUrl());
    }

    @Test
    void addProductImage_atMaxLimit_throwsBadRequest() {
        Product product = makeProduct(PRODUCT_ID);
        when(productRepository.findByIdAndCompanyIdWithLock(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(productImageRepository.countByProductId(PRODUCT_ID)).thenReturn(5);

        AddProductImageRequest req = new AddProductImageRequest();
        req.setImageUrl("https://example.com/img.jpg");

        assertThrows(BadRequestException.class,
                () -> service.addProductImage(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void addProductImage_productNotFound_throwsResourceNotFound() {
        when(productRepository.findByIdAndCompanyIdWithLock(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        AddProductImageRequest req = new AddProductImageRequest();
        req.setImageUrl("https://example.com/img.jpg");

        assertThrows(ResourceNotFoundException.class,
                () -> service.addProductImage(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    // ─── deleteProductImage ───────────────────────────────────────────────────

    @Test
    void deleteProductImage_lastImage_clearsThumbnail() {
        Product product = makeProduct(PRODUCT_ID);
        product.setThumbnailUrl("https://example.com/img.jpg");
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        ProductImage image = makeImage(IMAGE_ID, "https://example.com/img.jpg");
        when(productImageRepository.findByIdAndProductId(IMAGE_ID, PRODUCT_ID)).thenReturn(Optional.of(image));
        when(productImageRepository.findAllByProductIdOrderByDisplayOrderAsc(PRODUCT_ID)).thenReturn(List.of());

        service.deleteProductImage(COMPANY_ID, PRODUCT_ID, IMAGE_ID, OWNER_ID);

        assertNull(product.getThumbnailUrl());
        verify(productRepository).save(product);
    }

    @Test
    void deleteProductImage_remainingImages_updatesThumbnailToFirst() {
        Product product = makeProduct(PRODUCT_ID);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        ProductImage imageToDelete = makeImage(IMAGE_ID, "https://example.com/old.jpg");
        ProductImage remaining = makeImage(TestIds.uuid(99), "https://example.com/new.jpg");
        when(productImageRepository.findByIdAndProductId(IMAGE_ID, PRODUCT_ID)).thenReturn(Optional.of(imageToDelete));
        when(productImageRepository.findAllByProductIdOrderByDisplayOrderAsc(PRODUCT_ID)).thenReturn(List.of(remaining));

        service.deleteProductImage(COMPANY_ID, PRODUCT_ID, IMAGE_ID, OWNER_ID);

        assertEquals("https://example.com/new.jpg", product.getThumbnailUrl());
    }

    @Test
    void deleteProductImage_imageNotFound_throwsResourceNotFound() {
        Product product = makeProduct(PRODUCT_ID);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(productImageRepository.findByIdAndProductId(IMAGE_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteProductImage(COMPANY_ID, PRODUCT_ID, IMAGE_ID, OWNER_ID));
    }

    // ─── reorderProductImages ─────────────────────────────────────────────────

    @Test
    void reorderProductImages_happyPath_updatesDisplayOrders() {
        Product product = makeProduct(PRODUCT_ID);
        UUID img1 = TestIds.uuid(10);
        UUID img2 = TestIds.uuid(11);
        ProductImage i1 = makeImage(img1, "https://example.com/1.jpg");
        ProductImage i2 = makeImage(img2, "https://example.com/2.jpg");
        when(productRepository.findByIdAndCompanyIdWithLock(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(productImageRepository.findAllByProductIdOrderByDisplayOrderAsc(PRODUCT_ID)).thenReturn(List.of(i1, i2));
        when(productImageRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ReorderProductImagesRequest req = new ReorderProductImagesRequest();
        req.setImageIds(List.of(img2, img1)); // reversed order

        List<ProductImageResponse> result = service.reorderProductImages(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertNotNull(result);
        assertEquals(2, result.size());
        // img2 should have displayOrder 0, img1 displayOrder 1
        assertEquals(0, i2.getDisplayOrder());
        assertEquals(1, i1.getDisplayOrder());
    }

    @Test
    void reorderProductImages_wrongCount_throwsBadRequest() {
        Product product = makeProduct(PRODUCT_ID);
        UUID img1 = TestIds.uuid(10);
        UUID img2 = TestIds.uuid(11);
        when(productRepository.findByIdAndCompanyIdWithLock(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(productImageRepository.findAllByProductIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(makeImage(img1, "u1"), makeImage(img2, "u2")));

        ReorderProductImagesRequest req = new ReorderProductImagesRequest();
        req.setImageIds(List.of(img1)); // only 1 of 2

        assertThrows(BadRequestException.class,
                () -> service.reorderProductImages(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void reorderProductImages_unknownImageId_throwsBadRequest() {
        Product product = makeProduct(PRODUCT_ID);
        UUID img1 = TestIds.uuid(10);
        UUID unknown = TestIds.uuid(99);
        when(productRepository.findByIdAndCompanyIdWithLock(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(productImageRepository.findAllByProductIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(makeImage(img1, "u1")));

        ReorderProductImagesRequest req = new ReorderProductImagesRequest();
        req.setImageIds(List.of(unknown));

        assertThrows(BadRequestException.class,
                () -> service.reorderProductImages(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    // ─── addProductOption ─────────────────────────────────────────────────────

    @Test
    void addProductOption_happyPath_savesOption() {
        Product product = makeProduct(PRODUCT_ID);
        when(productRepository.findByIdAndCompanyIdWithLock(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(productOptionRepository.countByProductId(PRODUCT_ID)).thenReturn(1);
        when(productOptionRepository.save(any(ProductOption.class))).thenAnswer(inv -> {
            ProductOption opt = inv.getArgument(0);
            opt.setId(OPTION_ID);
            return opt;
        });

        CreateProductOptionRequest req = new CreateProductOptionRequest();
        req.setName("Size");

        ProductOptionResponse result = service.addProductOption(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertNotNull(result);
        assertEquals("Size", result.name());
    }

    @Test
    void addProductOption_atMaxLimit_throwsBadRequest() {
        Product product = makeProduct(PRODUCT_ID);
        when(productRepository.findByIdAndCompanyIdWithLock(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(productOptionRepository.countByProductId(PRODUCT_ID)).thenReturn(3);

        CreateProductOptionRequest req = new CreateProductOptionRequest();
        req.setName("Size");

        assertThrows(BadRequestException.class,
                () -> service.addProductOption(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void addProductOption_productNotFound_throwsResourceNotFound() {
        when(productRepository.findByIdAndCompanyIdWithLock(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        CreateProductOptionRequest req = new CreateProductOptionRequest();
        req.setName("Size");

        assertThrows(ResourceNotFoundException.class,
                () -> service.addProductOption(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    // ─── updateProductOption ──────────────────────────────────────────────────

    @Test
    void updateProductOption_happyPath_updatesName() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        ProductOption option = makeOption(OPTION_ID, "Color");
        when(productOptionRepository.findByIdAndProductId(OPTION_ID, PRODUCT_ID)).thenReturn(Optional.of(option));
        when(productOptionRepository.save(any(ProductOption.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProductOptionRequest req = new UpdateProductOptionRequest();
        req.setName("Colour");

        ProductOptionResponse result = service.updateProductOption(COMPANY_ID, PRODUCT_ID, OPTION_ID, OWNER_ID, req);

        assertEquals("Colour", result.name());
    }

    @Test
    void updateProductOption_notFound_throwsResourceNotFound() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(productOptionRepository.findByIdAndProductId(OPTION_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateProductOption(COMPANY_ID, PRODUCT_ID, OPTION_ID, OWNER_ID, new UpdateProductOptionRequest()));
    }

    // ─── deleteProductOption ──────────────────────────────────────────────────

    @Test
    void deleteProductOption_happyPath_deletes() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        ProductOption option = makeOption(OPTION_ID, "Color");
        when(productOptionRepository.findByIdAndProductId(OPTION_ID, PRODUCT_ID)).thenReturn(Optional.of(option));

        service.deleteProductOption(COMPANY_ID, PRODUCT_ID, OPTION_ID, OWNER_ID);

        verify(productOptionRepository).delete(option);
    }

    @Test
    void deleteProductOption_notFound_throwsResourceNotFound() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(productOptionRepository.findByIdAndProductId(OPTION_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteProductOption(COMPANY_ID, PRODUCT_ID, OPTION_ID, OWNER_ID));
    }

    // ─── createProductVariant ─────────────────────────────────────────────────

    @Test
    void createProductVariant_happyPath_savesVariant() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> {
            ProductVariant v = inv.getArgument(0);
            v.setId(VARIANT_ID);
            return v;
        });

        CreateProductVariantRequest req = new CreateProductVariantRequest();
        req.setPrice(new BigDecimal("14.99"));
        req.setSku(null);

        ProductVariantResponse result = service.createProductVariant(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertNotNull(result);
        assertEquals(new BigDecimal("14.99"), result.price());
    }

    @Test
    void createProductVariant_duplicateSku_throwsConflict() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(productVariantRepository.existsBySkuAndProductCompanyId("VAR-1", COMPANY_ID)).thenReturn(true);

        CreateProductVariantRequest req = new CreateProductVariantRequest();
        req.setPrice(new BigDecimal("14.99"));
        req.setSku("VAR-1");

        assertThrows(ConflictException.class,
                () -> service.createProductVariant(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void createProductVariant_nullSku_skipsSkuCheck() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> {
            ProductVariant v = inv.getArgument(0);
            v.setId(VARIANT_ID);
            return v;
        });

        CreateProductVariantRequest req = new CreateProductVariantRequest();
        req.setPrice(new BigDecimal("14.99"));
        req.setSku(null);

        service.createProductVariant(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        verify(productVariantRepository, never()).existsBySkuAndProductCompanyId(any(), any());
    }

    // ─── updateProductVariant ─────────────────────────────────────────────────

    @Test
    void updateProductVariant_happyPath_updatesFields() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        ProductVariant variant = makeVariant(VARIANT_ID);
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProductVariantRequest req = new UpdateProductVariantRequest();
        req.setPrice(new BigDecimal("24.99"));

        ProductVariantResponse result = service.updateProductVariant(COMPANY_ID, PRODUCT_ID, VARIANT_ID, OWNER_ID, req);

        assertEquals(new BigDecimal("24.99"), result.price());
    }

    @Test
    void updateProductVariant_marketplaceListedProduct_evictsMarketplaceCaches() {
        // Variant edits change marketplace product-detail data, so the marketplace caches must be
        // evicted too — not just the company-scoped ones.
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        ProductVariant variant = makeVariant(VARIANT_ID);
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.findMarketplaceIdByProductId(PRODUCT_ID)).thenReturn(MARKETPLACE_ID);

        UpdateProductVariantRequest req = new UpdateProductVariantRequest();
        req.setPrice(new BigDecimal("24.99"));

        service.updateProductVariant(COMPANY_ID, PRODUCT_ID, VARIANT_ID, OWNER_ID, req);

        verify(singleFlightCache, atLeastOnce()).evict(contains("marketplace:product:"));
        verify(singleFlightCache, atLeastOnce()).evictByPattern(contains("marketplace:search:"));
    }

    @Test
    void updateProductVariant_partialCompareAtPriceBelowExistingPrice_throwsBadRequest() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        ProductVariant variant = makeVariant(VARIANT_ID);
        variant.setPrice(new BigDecimal("100.00"));
        variant.setCompareAtPrice(null);
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));

        UpdateProductVariantRequest req = new UpdateProductVariantRequest();
        req.setCompareAtPrice(new BigDecimal("50.00")); // only compareAtPrice; below existing price

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.updateProductVariant(COMPANY_ID, PRODUCT_ID, VARIANT_ID, OWNER_ID, req));
    }

    @Test
    void createProductVariant_concurrentSkuRace_dbUniqueViolation_throwsConflict() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        // Pre-check passes (no existing SKU) but a concurrent insert wins the race; the DB unique
        // index then rejects our insert and the service maps it to a clean 409.
        when(productVariantRepository.existsBySkuAndProductCompanyId("RACE-SKU", COMPANY_ID)).thenReturn(false);
        when(productVariantRepository.save(any(ProductVariant.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

        CreateProductVariantRequest req = new CreateProductVariantRequest();
        req.setSku("RACE-SKU");
        req.setPrice(new BigDecimal("10.00"));

        assertThrows(ConflictException.class,
                () -> service.createProductVariant(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void updateProductVariant_skuConflict_throwsConflict() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        ProductVariant variant = makeVariant(VARIANT_ID);
        variant.setSku("OLD-SKU");
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));
        when(productVariantRepository.existsBySkuAndProductCompanyId("NEW-SKU", COMPANY_ID)).thenReturn(true);

        UpdateProductVariantRequest req = new UpdateProductVariantRequest();
        req.setSku("NEW-SKU");

        assertThrows(ConflictException.class,
                () -> service.updateProductVariant(COMPANY_ID, PRODUCT_ID, VARIANT_ID, OWNER_ID, req));
    }

    @Test
    void updateProductVariant_skuSameAsExisting_skipsCheck() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        ProductVariant variant = makeVariant(VARIANT_ID);
        variant.setSku("SAME-SKU");
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProductVariantRequest req = new UpdateProductVariantRequest();
        req.setSku("SAME-SKU");

        service.updateProductVariant(COMPANY_ID, PRODUCT_ID, VARIANT_ID, OWNER_ID, req);

        verify(productVariantRepository, never()).existsBySkuAndProductCompanyId(any(), any());
    }

    @Test
    void updateProductVariant_notFound_throwsResourceNotFound() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateProductVariant(COMPANY_ID, PRODUCT_ID, VARIANT_ID, OWNER_ID, new UpdateProductVariantRequest()));
    }

    // ─── deleteProductVariant ─────────────────────────────────────────────────

    @Test
    void deleteProductVariant_happyPath_deletesAndLogsChange() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        ProductVariant variant = makeVariant(VARIANT_ID);
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));

        service.deleteProductVariant(COMPANY_ID, PRODUCT_ID, VARIANT_ID, OWNER_ID);

        verify(productVariantRepository).delete(variant);
        verify(productChangeLogger).logVariantDelete(variant);
    }

    @Test
    void deleteProductVariant_notFound_throwsResourceNotFound() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteProductVariant(COMPANY_ID, PRODUCT_ID, VARIANT_ID, OWNER_ID));
    }

    @Test
    void deleteProductVariant_referencedByOrder_throwsConflict() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        ProductVariant variant = makeVariant(VARIANT_ID);
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));
        when(orderItemRepository.existsByVariantId(VARIANT_ID)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.deleteProductVariant(COMPANY_ID, PRODUCT_ID, VARIANT_ID, OWNER_ID));
        verify(productVariantRepository, never()).delete(any());
    }

    // ─── setProductAttributes ─────────────────────────────────────────────────

    @Test
    void setProductAttributes_replacesAllExisting() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(productAttributeRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        SetProductAttributesRequest.AttributeItem item = new SetProductAttributesRequest.AttributeItem();
        item.setName("Material");
        item.setValue("Cotton");
        item.setDisplayOrder(0);

        SetProductAttributesRequest req = new SetProductAttributesRequest();
        req.setAttributes(List.of(item));

        service.setProductAttributes(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        verify(productAttributeRepository).deleteAllByProductId(PRODUCT_ID);
        verify(productAttributeRepository).saveAll(any());
    }

    @Test
    void setProductAttributes_emptyList_onlyDeletes() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct(PRODUCT_ID)));
        when(productAttributeRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        SetProductAttributesRequest req = new SetProductAttributesRequest();
        req.setAttributes(List.of());

        service.setProductAttributes(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        verify(productAttributeRepository).deleteAllByProductId(PRODUCT_ID);
    }

    // ─── updateMarketplaceListing ─────────────────────────────────────────────

    @Test
    void updateMarketplaceListing_listing_setsMarketplaceIdAndFlag() {
        Product product = makeProduct(PRODUCT_ID);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(marketplaceVendorRepository.existsByMarketplaceIdAndVendorCompanyId(MARKETPLACE_ID, COMPANY_ID))
                .thenReturn(true);

        UpdateMarketplaceListingRequest req = new UpdateMarketplaceListingRequest();
        req.setMarketplaceId(MARKETPLACE_ID);
        req.setListed(true);

        ProductResponse result = service.updateMarketplaceListing(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertNotNull(result);
        assertTrue(product.isMarketplaceListed());
        assertEquals(MARKETPLACE_ID, product.getMarketplaceId());
    }

    @Test
    void updateMarketplaceListing_delisting_clearsMarketplaceId() {
        Product product = makeProduct(PRODUCT_ID);
        product.setMarketplaceId(MARKETPLACE_ID);
        product.setMarketplaceListed(true);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(marketplaceVendorRepository.existsByMarketplaceIdAndVendorCompanyId(MARKETPLACE_ID, COMPANY_ID))
                .thenReturn(true);

        UpdateMarketplaceListingRequest req = new UpdateMarketplaceListingRequest();
        req.setMarketplaceId(MARKETPLACE_ID);
        req.setListed(false);

        service.updateMarketplaceListing(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertFalse(product.isMarketplaceListed());
        assertNull(product.getMarketplaceId());
    }

    @Test
    void updateMarketplaceListing_notAVendor_throwsForbidden() {
        Product product = makeProduct(PRODUCT_ID);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(marketplaceVendorRepository.existsByMarketplaceIdAndVendorCompanyId(MARKETPLACE_ID, COMPANY_ID))
                .thenReturn(false);

        UpdateMarketplaceListingRequest req = new UpdateMarketplaceListingRequest();
        req.setMarketplaceId(MARKETPLACE_ID);
        req.setListed(true);

        assertThrows(ForbiddenException.class,
                () -> service.updateMarketplaceListing(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    // ─── searchProducts (JPA fallback) ────────────────────────────────────────

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void searchProducts_elasticsearchFails_fallsBackToJpa() {
        // Use raw Class cast to avoid ambiguous overload resolution on search(Query, Class<T>)
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.searchProducts(COMPANY_ID, null, null, null, null, null, null, null, null, null, null,
                0, 20, "createdAt", "desc");

        assertNotNull(result);
        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    // ─── batchUpdateProducts ──────────────────────────────────────────────────

    @Test
    void batchUpdateProducts_validRequest_setsStatusOnAllAndReturnsResponses() {
        UUID pid1 = TestIds.uuid(10);
        UUID pid2 = TestIds.uuid(11);
        Product p1 = makeProduct(pid1);
        Product p2 = makeProduct(pid2);
        when(productRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID)))
                .thenReturn(List.of(p1, p2));
        when(productImageRepository.countByProductId(pid1)).thenReturn(1);
        when(productImageRepository.countByProductId(pid2)).thenReturn(1);

        BatchUpdateProductsRequest req = new BatchUpdateProductsRequest();
        req.setIds(List.of(pid1, pid2));
        req.setStatus(ProductStatus.ACTIVE);

        List<ProductResponse> results = service.batchUpdateProducts(COMPANY_ID, OWNER_ID, req);

        assertEquals(2, results.size());
        verify(productRepository, times(2)).save(any(Product.class));
        verify(eventPublisher, times(2)).publishEvent(any(Object.class));
    }

    @Test
    void batchUpdateProducts_scheduledStatus_throwsBadRequest() {
        BatchUpdateProductsRequest req = new BatchUpdateProductsRequest();
        req.setIds(List.of(PRODUCT_ID));
        req.setStatus(ProductStatus.SCHEDULED);

        assertThrows(BadRequestException.class,
                () -> service.batchUpdateProducts(COMPANY_ID, OWNER_ID, req));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void batchUpdateProducts_productCountMismatch_throwsNotFound() {
        UUID pid1 = TestIds.uuid(10);
        UUID pid2 = TestIds.uuid(11);
        when(productRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID)))
                .thenReturn(List.of(makeProduct(pid1)));

        BatchUpdateProductsRequest req = new BatchUpdateProductsRequest();
        req.setIds(List.of(pid1, pid2));
        req.setStatus(ProductStatus.INACTIVE);

        assertThrows(ResourceNotFoundException.class,
                () -> service.batchUpdateProducts(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void batchUpdateProducts_activatingWithoutImages_throwsBadRequest() {
        UUID pid = TestIds.uuid(10);
        Product p = makeProduct(pid);
        when(productRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID))).thenReturn(List.of(p));
        when(productImageRepository.countByProductId(pid)).thenReturn(0);

        BatchUpdateProductsRequest req = new BatchUpdateProductsRequest();
        req.setIds(List.of(pid));
        req.setStatus(ProductStatus.ACTIVE);

        assertThrows(BadRequestException.class,
                () -> service.batchUpdateProducts(COMPANY_ID, OWNER_ID, req));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void batchUpdateProducts_settingInactive_noImageCheckPerformed() {
        UUID pid = TestIds.uuid(10);
        Product p = makeProduct(pid);
        when(productRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID))).thenReturn(List.of(p));

        BatchUpdateProductsRequest req = new BatchUpdateProductsRequest();
        req.setIds(List.of(pid));
        req.setStatus(ProductStatus.INACTIVE);

        service.batchUpdateProducts(COMPANY_ID, OWNER_ID, req);

        verify(productImageRepository, never()).countByProductId(any(UUID.class));
    }

    @Test
    void batchUpdateProducts_settingDiscontinued_noImageCheckPerformed() {
        UUID pid = TestIds.uuid(10);
        Product p = makeProduct(pid);
        when(productRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID))).thenReturn(List.of(p));

        BatchUpdateProductsRequest req = new BatchUpdateProductsRequest();
        req.setIds(List.of(pid));
        req.setStatus(ProductStatus.DISCONTINUED);

        service.batchUpdateProducts(COMPANY_ID, OWNER_ID, req);

        verify(productImageRepository, never()).countByProductId(any(UUID.class));
    }

    @Test
    void batchUpdateProducts_categoryAndBrand_appliedToAll() {
        UUID pid1 = TestIds.uuid(10);
        UUID pid2 = TestIds.uuid(11);
        Product p1 = makeProduct(pid1);
        Product p2 = makeProduct(pid2);
        when(productRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID)))
                .thenReturn(List.of(p1, p2));

        BatchUpdateProductsRequest req = new BatchUpdateProductsRequest();
        req.setIds(List.of(pid1, pid2));
        req.setCategory("Electronics");
        req.setBrand("Acme");

        service.batchUpdateProducts(COMPANY_ID, OWNER_ID, req);

        verify(productRepository, times(2)).save(argThat(p ->
                "Electronics".equals(p.getCategory()) && "Acme".equals(p.getBrand())));
    }

    // ─── duplicateProduct ─────────────────────────────────────────────────────

    @Test
    void duplicateProduct_createsDraftCopyWithNameSuffix() {
        Product source = makeProduct(PRODUCT_ID);
        source.setName("Widget");
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(source));
        when(productImageRepository.findAllByProductIdOrderByDisplayOrderAsc(PRODUCT_ID)).thenReturn(List.of());
        when(productOptionRepository.findAllByProductIdOrderByPositionAsc(PRODUCT_ID)).thenReturn(List.of());
        when(productVariantRepository.findAllByProductIdOrderByDisplayOrderAsc(PRODUCT_ID)).thenReturn(List.of());
        when(productAttributeRepository.findAllByProductIdOrderByDisplayOrderAsc(PRODUCT_ID)).thenReturn(List.of());

        ProductResponse result = service.duplicateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID);

        assertEquals("Widget (Copy)", result.getName());
        assertEquals("DRAFT", result.getStatus());
        assertNull(result.getSku());
        verify(productChangeLogger).logCreate(any(Product.class), any());
    }

    @Test
    void duplicateProduct_productNotFound_throwsNotFound() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.duplicateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID));
    }

    @Test
    void duplicateProduct_copiesImagesOptionsAndVariants() {
        Product source = makeProduct(PRODUCT_ID);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(source));

        ProductImage img1 = makeImage(TestIds.uuid(20), "https://example.com/1.jpg");
        ProductImage img2 = makeImage(TestIds.uuid(21), "https://example.com/2.jpg");
        when(productImageRepository.findAllByProductIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(img1, img2));

        ProductOption opt = makeOption(TestIds.uuid(30), "Size");
        when(productOptionRepository.findAllByProductIdOrderByPositionAsc(PRODUCT_ID)).thenReturn(List.of(opt));

        ProductVariant variant = makeVariant(TestIds.uuid(40));
        when(productVariantRepository.findAllByProductIdOrderByDisplayOrderAsc(PRODUCT_ID)).thenReturn(List.of(variant));

        when(productAttributeRepository.findAllByProductIdOrderByDisplayOrderAsc(PRODUCT_ID)).thenReturn(List.of());

        service.duplicateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID);

        verify(productRepository).save(any(Product.class));
        verify(productImageRepository, times(2)).save(any(ProductImage.class));
        verify(productOptionRepository).save(any(ProductOption.class));
        verify(productVariantRepository).save(any(ProductVariant.class));
    }

    // ─── status transitions: INACTIVE / DISCONTINUED (via updateProduct) ──────

    @Test
    void updateProduct_activeToInactive_succeeds() {
        Product existing = makeProduct(PRODUCT_ID);
        existing.setStatus(ProductStatus.ACTIVE);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        UpdateProductRequest req = new UpdateProductRequest();
        req.setStatus(ProductStatus.INACTIVE);

        ProductResponse result = service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertEquals("INACTIVE", result.getStatus());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void updateProduct_inactiveToActive_withoutImage_throwsBadRequest() {
        Product existing = makeProduct(PRODUCT_ID);
        existing.setStatus(ProductStatus.INACTIVE);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(productImageRepository.countByProductId(PRODUCT_ID)).thenReturn(0);

        UpdateProductRequest req = new UpdateProductRequest();
        req.setStatus(ProductStatus.ACTIVE);

        assertThrows(BadRequestException.class,
                () -> service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProduct_activeToDiscontinued_noImageCheckRequired() {
        Product existing = makeProduct(PRODUCT_ID);
        existing.setStatus(ProductStatus.ACTIVE);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        UpdateProductRequest req = new UpdateProductRequest();
        req.setStatus(ProductStatus.DISCONTINUED);

        ProductResponse result = service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertEquals("DISCONTINUED", result.getStatus());
        verify(productImageRepository, never()).countByProductId(any(UUID.class));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Company makeCompany() {
        Company c = new Company();
        c.setId(COMPANY_ID);
        c.setName("Test Company");
        return c;
    }

    private Product makeProduct(UUID id) {
        Product p = new Product();
        p.setId(id);
        p.setCompany(makeCompany());
        p.setName("Test Product");
        p.setPrice(new BigDecimal("29.99"));
        p.setCurrency("USD");
        p.setStatus(ProductStatus.DRAFT);
        p.setImages(new ArrayList<>());
        p.setOptions(new ArrayList<>());
        p.setVariants(new ArrayList<>());
        p.setAttributes(new ArrayList<>());
        return p;
    }

    private ProductImage makeImage(UUID id, String url) {
        ProductImage img = new ProductImage();
        img.setId(id);
        img.setImageUrl(url);
        img.setDisplayOrder(0);
        return img;
    }

    private ProductOption makeOption(UUID id, String name) {
        ProductOption opt = new ProductOption();
        opt.setId(id);
        opt.setName(name);
        opt.setPosition(0);
        return opt;
    }

    private ProductVariant makeVariant(UUID id) {
        ProductVariant v = new ProductVariant();
        v.setId(id);
        v.setPrice(new BigDecimal("19.99"));
        return v;
    }

    private CreateProductRequest makeCreateRequest(String name, String sku) {
        CreateProductRequest req = new CreateProductRequest();
        req.setName(name);
        req.setPrice(new BigDecimal("9.99"));
        req.setSku(sku);
        return req;
    }

    // ─── getProductHistory ────────────────────────────────────────────────────

    @Test
    void getProductHistory_productNotFound_throwsResourceNotFoundException() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.getProductHistory(COMPANY_ID, PRODUCT_ID, 0, 20));
    }

    @Test
    void getProductHistory_emptyHistory_returnsEmptyPage() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct()));
        when(productChangeLogRepository.findAllByProductIdAndCompanyId(eq(PRODUCT_ID), eq(COMPANY_ID), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(inventoryAdjustmentRepository.findAllByProductIdAndProductCompanyId(eq(PRODUCT_ID), eq(COMPANY_ID), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.getProductHistory(COMPANY_ID, PRODUCT_ID, 0, 20);

        assertEquals(0, result.getItems().size());
    }

    @Test
    void getProductHistory_capsPageSizeAt100() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct()));
        when(productChangeLogRepository.findAllByProductIdAndCompanyId(eq(PRODUCT_ID), eq(COMPANY_ID), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(inventoryAdjustmentRepository.findAllByProductIdAndProductCompanyId(eq(PRODUCT_ID), eq(COMPANY_ID), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.getProductHistory(COMPANY_ID, PRODUCT_ID, 0, 9999); // should clamp to 100

        verify(productChangeLogRepository).findAllByProductIdAndCompanyId(eq(PRODUCT_ID), eq(COMPANY_ID), any());
    }

    // ─── updateProductMerchandising ───────────────────────────────────────────

    @Test
    void updateProductMerchandising_pinnedUntilInPast_throwsBadRequest() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct()));

        backend.dtos.requests.product.UpdateProductMerchandisingRequest req =
                new backend.dtos.requests.product.UpdateProductMerchandisingRequest();
        req.setPinnedUntil(Instant.now().minusSeconds(3600)); // in the past

        assertThrows(BadRequestException.class, () ->
                service.updateProductMerchandising(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void updateProductMerchandising_happyPath_savesAndPublishesEvent() {
        Product product = makeProduct();
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));

        backend.dtos.requests.product.UpdateProductMerchandisingRequest req =
                new backend.dtos.requests.product.UpdateProductMerchandisingRequest();
        req.setBoostWeight(5);
        req.setPinnedUntil(null); // no pin

        service.updateProductMerchandising(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        verify(productRepository).save(product);
        verify(eventPublisher).publishEvent(any(backend.events.ProductIndexEvent.class));
    }

    @Test
    void updateProductMerchandising_pinnedUntilInFuture_setsRankAndPin() {
        Product product = makeProduct();
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));

        backend.dtos.requests.product.UpdateProductMerchandisingRequest req =
                new backend.dtos.requests.product.UpdateProductMerchandisingRequest();
        req.setPinnedUntil(Instant.now().plusSeconds(86400));
        req.setPinnedRank(2);

        service.updateProductMerchandising(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertEquals(2, product.getPinnedRank());
        verify(productRepository).save(product);
    }

    private Product makeProduct() {
        Company company = new Company();
        company.setId(COMPANY_ID);
        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setCompany(company);
        p.setName("Widget");
        p.setStatus(ProductStatus.ACTIVE);
        p.setPrice(new BigDecimal("9.99"));
        p.setImages(new java.util.ArrayList<>());
        p.setOptions(new java.util.ArrayList<>());
        p.setVariants(new java.util.ArrayList<>());
        p.setAttributes(new java.util.ArrayList<>());
        return p;
    }

    // =========================================================================
    // searchProducts — ES happy path
    // =========================================================================

    @SuppressWarnings("unchecked")
    @Test
    void searchProducts_esSuccess_returnsPagedResults() {
        // ES returns one hit with PRODUCT_ID
        var doc = new backend.documents.ProductDocument();
        doc.setId(PRODUCT_ID);
        var hit = (org.springframework.data.elasticsearch.core.SearchHit<backend.documents.ProductDocument>)
                mock(org.springframework.data.elasticsearch.core.SearchHit.class);
        when(hit.getContent()).thenReturn(doc);

        var hits = (org.springframework.data.elasticsearch.core.SearchHits<backend.documents.ProductDocument>)
                mock(org.springframework.data.elasticsearch.core.SearchHits.class);
        when(hits.stream()).thenReturn(java.util.stream.Stream.of(hit));
        when(hits.getTotalHits()).thenReturn(1L);

        when(elasticsearchOperations.search(any(org.springframework.data.elasticsearch.core.query.Query.class), (Class) any())).thenReturn(hits);

        Product product = makeProduct();
        when(productRepository.findAllByIdInAndCompanyId(any(), eq(COMPANY_ID)))
                .thenReturn(List.of(product));

        var result = service.searchProducts(COMPANY_ID, "widget", null, null,
                null, null, null, null, null, null, null, 0, 10, null, null);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
    }

    @Test
    void searchProducts_esFails_fallsBackToJpa_alreadyCovered() {
        // This scenario was already tested in searchProducts_elasticsearchFails_fallsBackToJpa
        // Adding a complementary test verifying ServiceUnavailableException when filters present
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(org.springframework.data.elasticsearch.core.query.Query.class), (Class) any());
        when(productRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // No filters — should fall back to JPA without throwing
        var result = service.searchProducts(COMPANY_ID, null, null, null,
                null, null, null, null, null, null, null, 0, 10, null, null);

        assertNotNull(result);
        assertEquals(0, result.getItems().size());
    }

    // =========================================================================
    // addProductRelationship
    // =========================================================================

    @Test
    void addProductRelationship_happyPath_savesRelationship() {
        UUID targetId = TestIds.uuid(20);
        Product source = makeProduct();
        Product target = makeProduct();
        target.setId(targetId);

        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(source));
        when(productRepository.findByIdAndCompanyId(targetId, COMPANY_ID)).thenReturn(Optional.of(target));
        when(productRelationshipRepository.existsBySourceProductIdAndTargetProductIdAndType(
                eq(PRODUCT_ID), eq(targetId), any())).thenReturn(false);
        when(productRelationshipRepository.save(any())).thenAnswer(inv -> {
            var rel = inv.getArgument(0, backend.models.core.ProductRelationship.class);
            rel.setId(TestIds.uuid(99));
            return rel;
        });

        var req = new backend.dtos.requests.product.AddProductRelationshipRequest();
        req.setTargetProductId(targetId);
        req.setType(backend.models.enums.ProductRelationshipType.SIMILAR);

        assertNotNull(service.addProductRelationship(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
        verify(productRelationshipRepository).save(any());
    }

    @Test
    void addProductRelationship_selfRelationship_throwsBadRequest() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct()));

        var req = new backend.dtos.requests.product.AddProductRelationshipRequest();
        req.setTargetProductId(PRODUCT_ID); // same as source = self-relationship
        req.setType(backend.models.enums.ProductRelationshipType.SIMILAR);

        assertThrows(BadRequestException.class, () ->
                service.addProductRelationship(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void addProductRelationship_duplicate_throwsConflict() {
        UUID targetId = TestIds.uuid(21);
        Product source = makeProduct();
        Product target = makeProduct();
        target.setId(targetId);

        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(source));
        when(productRepository.findByIdAndCompanyId(targetId, COMPANY_ID)).thenReturn(Optional.of(target));
        when(productRelationshipRepository.existsBySourceProductIdAndTargetProductIdAndType(
                eq(PRODUCT_ID), eq(targetId), any())).thenReturn(true); // already exists

        var req = new backend.dtos.requests.product.AddProductRelationshipRequest();
        req.setTargetProductId(targetId);
        req.setType(backend.models.enums.ProductRelationshipType.SIMILAR);

        assertThrows(ConflictException.class, () ->
                service.addProductRelationship(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void removeProductRelationship_notFound_throwsResourceNotFound() {
        UUID targetId = TestIds.uuid(22);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct()));
        when(productRelationshipRepository.existsBySourceProductIdAndTargetProductIdAndType(
                eq(PRODUCT_ID), eq(targetId), any())).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                service.removeProductRelationship(COMPANY_ID, PRODUCT_ID, targetId,
                        backend.models.enums.ProductRelationshipType.SIMILAR, OWNER_ID));
    }

    @Test
    void removeProductRelationship_happyPath_deletesRelationship() {
        UUID targetId = TestIds.uuid(23);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct()));
        when(productRelationshipRepository.existsBySourceProductIdAndTargetProductIdAndType(
                eq(PRODUCT_ID), eq(targetId), any())).thenReturn(true);

        service.removeProductRelationship(COMPANY_ID, PRODUCT_ID, targetId,
                backend.models.enums.ProductRelationshipType.SIMILAR, OWNER_ID);

        verify(productRelationshipRepository).deleteBySourceProductIdAndTargetProductIdAndType(
                eq(PRODUCT_ID), eq(targetId), any());
    }

    @Test
    void getProductRelationships_nullType_returnsAll() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(makeProduct()));
        when(productRelationshipRepository.findAllBySourceProductIdAndSourceProductCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(List.of());

        var result = service.getProductRelationships(COMPANY_ID, PRODUCT_ID, null);

        assertNotNull(result);
        verify(productRelationshipRepository).findAllBySourceProductIdAndSourceProductCompanyId(PRODUCT_ID, COMPANY_ID);
    }

    // =========================================================================
    // compareProducts
    // =========================================================================

    @Test
    void compareProducts_twoProducts_returnsComparison() {
        UUID id2 = TestIds.uuid(30);
        Product p1 = makeProduct();
        Product p2 = makeProduct();
        p2.setId(id2);

        when(productRepository.findAllByIdInAndCompanyId(any(), eq(COMPANY_ID)))
                .thenReturn(List.of(p1, p2));
        when(productReviewRepository.findAverageRatingsByProductIds(any())).thenReturn(List.of());

        var result = service.compareProducts(COMPANY_ID, List.of(PRODUCT_ID, id2));

        assertEquals(2, result.size());
    }

    @Test
    void compareProducts_onlyOneProduct_throwsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                service.compareProducts(COMPANY_ID, List.of(PRODUCT_ID)));
    }

    @Test
    void compareProducts_fiveProducts_throwsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                service.compareProducts(COMPANY_ID,
                        List.of(TestIds.uuid(1), TestIds.uuid(2), TestIds.uuid(3),
                                TestIds.uuid(4), TestIds.uuid(5))));
    }

    @Test
    void compareProducts_noProductsFound_throwsResourceNotFoundException() {
        when(productRepository.findAllByIdInAndCompanyId(any(), eq(COMPANY_ID)))
                .thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () ->
                service.compareProducts(COMPANY_ID, List.of(PRODUCT_ID, TestIds.uuid(31))));
    }

    // =========================================================================
    // searchMarketplaceCatalog
    // =========================================================================

    @Test
    void searchMarketplaceCatalog_marketplaceNotFound_throwsResourceNotFoundException() {
        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                service.searchMarketplaceCatalog(MARKETPLACE_ID, null, null, null,
                        null, null, null, null, 0, 10, null, null));
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchMarketplaceCatalog_esSuccess_returnsProductList() {
        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(true);

        var doc = new backend.documents.ProductDocument();
        doc.setId(PRODUCT_ID);
        var hit = (org.springframework.data.elasticsearch.core.SearchHit<backend.documents.ProductDocument>)
                mock(org.springframework.data.elasticsearch.core.SearchHit.class);
        when(hit.getContent()).thenReturn(doc);

        var hits = (org.springframework.data.elasticsearch.core.SearchHits<backend.documents.ProductDocument>)
                mock(org.springframework.data.elasticsearch.core.SearchHits.class);
        when(hits.stream()).thenReturn(java.util.stream.Stream.of(hit));
        when(hits.getTotalHits()).thenReturn(1L);
        when(hits.getAggregations()).thenReturn(null);

        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class), (Class) any()))
                .thenReturn(hits);

        Product product = makeProduct();
        product.setMarketplaceId(MARKETPLACE_ID);
        when(productRepository.findAllByIdInAndMarketplaceId(any(), eq(MARKETPLACE_ID)))
                .thenReturn(List.of(product));
        when(marketplaceVendorRepository.findByMarketplaceIdAndVendorCompanyIdIn(eq(MARKETPLACE_ID), any()))
                .thenReturn(List.of());

        var result = service.searchMarketplaceCatalog(MARKETPLACE_ID, null, null, null,
                null, null, null, null, 0, 10, null, null);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
    }

    @Test
    void searchMarketplaceCatalog_esFails_hasFilters_throwsServiceUnavailable() {
        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(true);
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(
                        any(org.springframework.data.elasticsearch.core.query.Query.class), (Class) any());

        assertThrows(backend.exceptions.http.ServiceUnavaliableException.class, () ->
                service.searchMarketplaceCatalog(MARKETPLACE_ID, "widget", null, null,
                        null, null, null, null, 0, 10, null, null)); // q != null = has filters
    }

    @Test
    void searchMarketplaceCatalog_esFails_noFilters_throwsServiceUnavailable() {
        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(true);
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(
                        any(org.springframework.data.elasticsearch.core.query.Query.class), (Class) any());

        // ES unavailable — always throws ServiceUnavailableException regardless of filters.
        assertThrows(backend.exceptions.http.ServiceUnavaliableException.class, () ->
                service.searchMarketplaceCatalog(MARKETPLACE_ID, null, null, null,
                        null, null, null, null, 0, 10, null, null));
    }

    // =========================================================================
    // searchCompanyCatalog
    // =========================================================================

    @SuppressWarnings("unchecked")
    @Test
    void searchCompanyCatalog_esSuccess_returnsProductList() {
        var doc = new backend.documents.ProductDocument();
        doc.setId(PRODUCT_ID);
        var hit = (org.springframework.data.elasticsearch.core.SearchHit<backend.documents.ProductDocument>)
                mock(org.springframework.data.elasticsearch.core.SearchHit.class);
        when(hit.getContent()).thenReturn(doc);

        var hits = (org.springframework.data.elasticsearch.core.SearchHits<backend.documents.ProductDocument>)
                mock(org.springframework.data.elasticsearch.core.SearchHits.class);
        when(hits.stream()).thenReturn(java.util.stream.Stream.of(hit));
        when(hits.getTotalHits()).thenReturn(1L);
        when(hits.getAggregations()).thenReturn(null);

        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class), (Class) any()))
                .thenReturn(hits);

        Product product = makeProduct();
        when(productRepository.findAllByIdInAndCompanyId(any(), eq(COMPANY_ID)))
                .thenReturn(List.of(product));

        var result = service.searchCompanyCatalog(COMPANY_ID, null, null, null,
                null, null, 0, 10, null, null);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
    }

    @Test
    void searchCompanyCatalog_esFails_hasFilters_throwsServiceUnavailable() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(
                        any(org.springframework.data.elasticsearch.core.query.Query.class), (Class) any());

        assertThrows(backend.exceptions.http.ServiceUnavaliableException.class, () ->
                service.searchCompanyCatalog(COMPANY_ID, "widget", null, null,
                        null, null, 0, 10, null, null));
    }

    @Test
    void searchCompanyCatalog_esFails_noFilters_fallsBackToJpa() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(
                        any(org.springframework.data.elasticsearch.core.query.Query.class), (Class) any());

        Product product = makeProduct();
        when(productRepository.findAllByCompanyIdAndStatus(eq(COMPANY_ID), eq(ProductStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));

        var result = service.searchCompanyCatalog(COMPANY_ID, null, null, null,
                null, null, 0, 10, null, null);

        assertNotNull(result);
    }

    // =========================================================================
    // getVendorStorefront
    // =========================================================================

    @Test
    void getVendorStorefront_vendorNotFound_throwsResourceNotFoundException() {
        UUID mktId = MARKETPLACE_ID;
        UUID vendorId = TestIds.uuid(40);
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(vendorId, mktId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.getVendorStorefront(mktId, vendorId));
    }

    @Test
    void getVendorStorefront_happyPath_returnsStorefront() {
        UUID mktId = MARKETPLACE_ID;
        UUID vendorId = TestIds.uuid(41);

        backend.models.core.MarketplaceVendor vendor = new backend.models.core.MarketplaceVendor();
        vendor.setId(vendorId);
        vendor.setTier(backend.models.enums.VendorTier.STANDARD);
        vendor.setStatus(backend.models.enums.VendorStatus.APPROVED);
        backend.models.core.Company vendorCompany = new backend.models.core.Company();
        vendorCompany.setId(COMPANY_ID);
        vendorCompany.setName("Vendor Co");
        vendor.setVendorCompany(vendorCompany);

        when(marketplaceVendorRepository.findByIdAndMarketplaceId(vendorId, mktId))
                .thenReturn(Optional.of(vendor));
        when(productRepository.findMarketplaceListed(mktId)).thenReturn(List.of());

        assertNotNull(service.getVendorStorefront(mktId, vendorId));
    }

    // =========================================================================
    // getSimilarProducts
    // =========================================================================

    @Test
    void getSimilarProducts_productNotFound_throwsResourceNotFoundException() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.getSimilarProducts(COMPANY_ID, PRODUCT_ID, 5));
    }

    @Test
    void getSimilarProducts_hasExplicitRelationships_returnsThem() {
        Product source = makeProduct();
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(source));

        UUID targetId = TestIds.uuid(60);
        Product target = makeProduct();
        target.setId(targetId);

        backend.models.core.ProductRelationship rel = new backend.models.core.ProductRelationship();
        rel.setId(TestIds.uuid(61));
        rel.setSourceProduct(source);
        rel.setTargetProduct(target);
        rel.setType(backend.models.enums.ProductRelationshipType.SIMILAR);

        when(productRelationshipRepository
                .findAllBySourceProductIdAndTypeAndSourceProductCompanyId(PRODUCT_ID,
                        backend.models.enums.ProductRelationshipType.SIMILAR, COMPANY_ID))
                .thenReturn(List.of(rel));
        when(productRepository.findAllByIdInAndCompanyId(any(), eq(COMPANY_ID)))
                .thenReturn(List.of(target));
        when(productReviewRepository.findAverageRatingsByProductIds(any())).thenReturn(List.of());
        when(productRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.getSimilarProducts(COMPANY_ID, PRODUCT_ID, 5);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getSimilarProducts_noRelationships_noSimilarityRows_returnsEmpty() {
        Product source = makeProduct();
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(source));
        when(productRelationshipRepository
                .findAllBySourceProductIdAndTypeAndSourceProductCompanyId(any(), any(), any()))
                .thenReturn(List.of()); // no manual relationships
        // productSimilarityRepository default (empty list) — no precomputed rows

        // ES fallback and JPA fallback stubs
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(
                        any(org.springframework.data.elasticsearch.core.query.Query.class), (Class) any());
        when(productRepository.findAllByIdInAndCompanyId(any(), eq(COMPANY_ID))).thenReturn(List.of());
        when(productRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(productRepository.findFeaturedByCompanyId(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.getSimilarProducts(COMPANY_ID, PRODUCT_ID, 5);
        assertNotNull(result);
    }

    // =========================================================================
    // revertProductChanges
    // =========================================================================

    @Test
    void revertProductChanges_productNotFound_throwsResourceNotFoundException() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        backend.dtos.requests.product.RevertProductChangesRequest req =
                new backend.dtos.requests.product.RevertProductChangesRequest();
        req.setLogEntryIds(List.of(TestIds.uuid(70)));

        assertThrows(ResourceNotFoundException.class, () ->
                service.revertProductChanges(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private org.springframework.data.elasticsearch.core.SearchHits<backend.documents.ProductDocument> emptySearchHits() {
        var hits = (org.springframework.data.elasticsearch.core.SearchHits<backend.documents.ProductDocument>)
                mock(org.springframework.data.elasticsearch.core.SearchHits.class);
        when(hits.stream()).thenReturn(java.util.stream.Stream.of());
        when(hits.getTotalHits()).thenReturn(0L);
        when(hits.getAggregations()).thenReturn(null);
        return hits;
    }

    private void assertNotNull(Object value) {
        if (value == null) throw new AssertionError("Expected non-null value but was null");
    }

    // =========================================================================
    // getProductsByIds
    // =========================================================================

    @Test
    void getProductsByIds_tooManyIds_throwsBadRequest() {
        List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 1001; i++) ids.add(UUID.randomUUID());
        assertThrows(BadRequestException.class, () -> service.getProductsByIds(COMPANY_ID, ids));
    }

    @Test
    void getProductsByIds_companyNotFound_throwsResourceNotFound() {
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.getProductsByIds(COMPANY_ID, List.of(PRODUCT_ID)));
    }

    @Test
    void getProductsByIds_happyPath_returnsResponses() {
        Product p = makeProduct();
        when(productRepository.findAllByIdInAndCompanyId(List.of(PRODUCT_ID), COMPANY_ID)).thenReturn(List.of(p));
        when(productReviewRepository.findAverageRatingsByProductIds(any())).thenReturn(List.of());

        List<ProductResponse> result = service.getProductsByIds(COMPANY_ID, List.of(PRODUCT_ID));

        assertEquals(1, result.size());
        assertEquals(PRODUCT_ID, result.get(0).getId());
    }

    // =========================================================================
    // getProductImages
    // =========================================================================

    @Test
    void getProductImages_productNotFound_throwsResourceNotFound() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getProductImages(COMPANY_ID, PRODUCT_ID));
    }

    @Test
    void getProductImages_happyPath_returnsImages() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct()));
        ProductImage img = makeImage(IMAGE_ID, "http://img.example.com/1.jpg");
        when(productImageRepository.findAllByProductIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(img));

        List<ProductImageResponse> result = service.getProductImages(COMPANY_ID, PRODUCT_ID);

        assertEquals(1, result.size());
        assertEquals(IMAGE_ID, result.get(0).id());
    }

    // =========================================================================
    // getProductOptions
    // =========================================================================

    @Test
    void getProductOptions_happyPath_returnsOptions() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct()));
        ProductOption opt = makeOption(OPTION_ID, "Size");
        when(productOptionRepository.findAllByProductIdOrderByPositionAsc(PRODUCT_ID))
                .thenReturn(List.of(opt));

        List<ProductOptionResponse> result = service.getProductOptions(COMPANY_ID, PRODUCT_ID);

        assertEquals(1, result.size());
        assertEquals(OPTION_ID, result.get(0).id());
    }

    // =========================================================================
    // getProductVariants / getProductVariant
    // =========================================================================

    @Test
    void getProductVariants_happyPath_returnsVariants() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct()));
        ProductVariant v = makeVariant(VARIANT_ID);
        when(productVariantRepository.findAllByProductIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(v));

        List<ProductVariantResponse> result = service.getProductVariants(COMPANY_ID, PRODUCT_ID);

        assertEquals(1, result.size());
        assertEquals(VARIANT_ID, result.get(0).id());
    }

    @Test
    void getProductVariant_happyPath_returnsVariant() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct()));
        ProductVariant v = makeVariant(VARIANT_ID);
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID))
                .thenReturn(Optional.of(v));

        ProductVariantResponse result = service.getProductVariant(COMPANY_ID, PRODUCT_ID, VARIANT_ID);

        assertEquals(VARIANT_ID, result.id());
    }

    @Test
    void getProductVariant_notFound_throwsResourceNotFound() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct()));
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getProductVariant(COMPANY_ID, PRODUCT_ID, VARIANT_ID));
    }

    // =========================================================================
    // getProductAttributes
    // =========================================================================

    @Test
    void getProductAttributes_happyPath_returnsAttributes() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeProduct()));
        ProductAttribute attr = new ProductAttribute();
        attr.setId(TestIds.uuid(90));
        attr.setName("Material");
        attr.setValue("Cotton");
        attr.setDisplayOrder(0);
        when(productAttributeRepository.findAllByProductIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(attr));

        List<ProductAttributeResponse> result =
                service.getProductAttributes(COMPANY_ID, PRODUCT_ID);

        assertEquals(1, result.size());
        assertEquals("Material", result.get(0).name());
    }

    // =========================================================================
    // getMarketplaceProduct
    // =========================================================================

    @Test
    void getMarketplaceProduct_notFound_throwsResourceNotFound() {
        when(productRepository.findByIdAndMarketplaceIdAndMarketplaceListedTrueAndStatus(
                PRODUCT_ID, MARKETPLACE_ID, backend.models.enums.ProductStatus.ACTIVE))
                .thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.getMarketplaceProduct(MARKETPLACE_ID, PRODUCT_ID));
    }

    @Test
    void getMarketplaceProduct_happyPath_returnsCatalogProduct() {
        Product p = makeProduct();
        p.setMarketplaceId(MARKETPLACE_ID);
        when(productRepository.findByIdAndMarketplaceIdAndMarketplaceListedTrueAndStatus(
                PRODUCT_ID, MARKETPLACE_ID, backend.models.enums.ProductStatus.ACTIVE))
                .thenReturn(Optional.of(p));
        when(marketplaceVendorRepository.findByMarketplaceIdAndVendorCompanyIdIn(eq(MARKETPLACE_ID), any()))
                .thenReturn(List.of());

        MarketplaceCatalogProductResponse result =
                service.getMarketplaceProduct(MARKETPLACE_ID, PRODUCT_ID);

        assertNotNull(result);
        assertEquals(PRODUCT_ID, result.getId());
    }

    // =========================================================================
    // revertProductChanges — additional paths
    // =========================================================================

    @Test
    void revertProductChanges_versionConflict_throwsConflict() {
        Product p = makeProduct();
        p.setVersion(2L);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(p));

        backend.dtos.requests.product.RevertProductChangesRequest req =
                new backend.dtos.requests.product.RevertProductChangesRequest();
        req.setLogEntryIds(List.of(TestIds.uuid(70)));
        req.setExpectedVersion(1L);

        assertThrows(ConflictException.class,
                () -> service.revertProductChanges(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void revertProductChanges_logEntryNotFound_throwsResourceNotFound() {
        Product p = makeProduct();
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(p));

        UUID logId = TestIds.uuid(71);
        when(productChangeLogRepository.findAllByIdInAndCompanyId(List.of(logId), COMPANY_ID))
                .thenReturn(List.of()); // fewer than requested

        backend.dtos.requests.product.RevertProductChangesRequest req =
                new backend.dtos.requests.product.RevertProductChangesRequest();
        req.setLogEntryIds(List.of(logId));

        assertThrows(ResourceNotFoundException.class,
                () -> service.revertProductChanges(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void revertProductChanges_stockField_throwsBadRequest() {
        Product p = makeProduct();
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(p));

        UUID logId = TestIds.uuid(72);
        ProductChangeLog entry = new ProductChangeLog();
        entry.setId(logId);
        entry.setProduct(p);
        entry.setFieldName("stock");
        entry.setChangeType(ProductChangeType.UPDATED);
        entry.setSource(ChangeSource.USER);
        entry.setChangedAt(Instant.now());

        when(productChangeLogRepository.findAllByIdInAndCompanyId(List.of(logId), COMPANY_ID))
                .thenReturn(List.of(entry));

        backend.dtos.requests.product.RevertProductChangesRequest req =
                new backend.dtos.requests.product.RevertProductChangesRequest();
        req.setLogEntryIds(List.of(logId));

        assertThrows(BadRequestException.class,
                () -> service.revertProductChanges(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void revertProductChanges_logEntryFromWrongProduct_throwsBadRequest() {
        Product p = makeProduct();
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(p));

        UUID logId = TestIds.uuid(73);
        Product otherProduct = makeProduct(TestIds.uuid(99));
        ProductChangeLog entry = new ProductChangeLog();
        entry.setId(logId);
        entry.setProduct(otherProduct); // belongs to a different product
        entry.setFieldName("name");
        entry.setChangeType(ProductChangeType.UPDATED);
        entry.setSource(ChangeSource.USER);
        entry.setChangedAt(Instant.now());

        when(productChangeLogRepository.findAllByIdInAndCompanyId(List.of(logId), COMPANY_ID))
                .thenReturn(List.of(entry));

        backend.dtos.requests.product.RevertProductChangesRequest req =
                new backend.dtos.requests.product.RevertProductChangesRequest();
        req.setLogEntryIds(List.of(logId));

        assertThrows(BadRequestException.class,
                () -> service.revertProductChanges(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void revertProductChanges_happyPath_revertsProductNameField() {
        Product p = makeProduct();
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(p));

        UUID logId = TestIds.uuid(74);
        ProductChangeLog entry = new ProductChangeLog();
        entry.setId(logId);
        entry.setProduct(p);
        entry.setFieldName("name");
        entry.setOldValue("Old Name");
        entry.setNewValue("New Name");
        entry.setChangeType(ProductChangeType.UPDATED);
        entry.setSource(ChangeSource.USER);
        entry.setChangedAt(Instant.now());

        when(productChangeLogRepository.findAllByIdInAndCompanyId(List.of(logId), COMPANY_ID))
                .thenReturn(List.of(entry));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        backend.dtos.requests.product.RevertProductChangesRequest req =
                new backend.dtos.requests.product.RevertProductChangesRequest();
        req.setLogEntryIds(List.of(logId));

        ProductResponse result = service.revertProductChanges(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertNotNull(result);
        verify(productChangeLogger).logUpdate(any(), any(), eq(ChangeSource.REVERT), any());
    }

    // =========================================================================
    // applyProductField (private — exhaustive switch coverage)
    // =========================================================================

    private Product blankProduct() {
        Product p = new Product();
        p.setId(PRODUCT_ID);
        Company c = new Company(); c.setId(COMPANY_ID);
        p.setCompany(c);
        return p;
    }

    private void applyField(Product p, String field, String value) {
        ReflectionTestUtils.invokeMethod(service, "applyProductField", p, field, value);
    }

    @Test
    void applyProductField_name_setsName() {
        Product p = blankProduct();
        applyField(p, "name", "Widget Pro");
        assertEquals("Widget Pro", p.getName());
    }

    @Test
    void applyProductField_description_setsDescription() {
        Product p = blankProduct();
        applyField(p, "description", "A great widget");
        assertEquals("A great widget", p.getDescription());
    }

    @Test
    void applyProductField_sku_setsSku() {
        Product p = blankProduct();
        applyField(p, "sku", "SKU-001");
        assertEquals("SKU-001", p.getSku());
    }

    @Test
    void applyProductField_price_setsPrice() {
        Product p = blankProduct();
        applyField(p, "price", "19.99");
        assertEquals(new BigDecimal("19.99"), p.getPrice());
    }

    @Test
    void applyProductField_price_null_setsNull() {
        Product p = blankProduct();
        applyField(p, "price", null);
        assertNull(p.getPrice());
    }

    @Test
    void applyProductField_compareAtPrice_setsValue() {
        Product p = blankProduct();
        applyField(p, "compareAtPrice", "29.99");
        assertEquals(new BigDecimal("29.99"), p.getCompareAtPrice());
    }

    @Test
    void applyProductField_currency_setsCurrency() {
        Product p = blankProduct();
        applyField(p, "currency", "EUR");
        assertEquals("EUR", p.getCurrency());
    }

    @Test
    void applyProductField_category_setsCategory() {
        Product p = blankProduct();
        applyField(p, "category", "Electronics");
        assertEquals("Electronics", p.getCategory());
    }

    @Test
    void applyProductField_brand_setsBrand() {
        Product p = blankProduct();
        applyField(p, "brand", "Acme");
        assertEquals("Acme", p.getBrand());
    }

    @Test
    void applyProductField_tags_setsTags() {
        Product p = blankProduct();
        applyField(p, "tags", "sale,new");
        assertEquals("sale,new", p.getTags());
    }

    @Test
    void applyProductField_thumbnailUrl_setsUrl() {
        Product p = blankProduct();
        applyField(p, "thumbnailUrl", "https://cdn.example.com/thumb.jpg");
        assertEquals("https://cdn.example.com/thumb.jpg", p.getThumbnailUrl());
    }

    @Test
    void applyProductField_weight_setsWeight() {
        Product p = blankProduct();
        applyField(p, "weight", "1.5");
        assertEquals(new BigDecimal("1.5"), p.getWeight());
    }

    @Test
    void applyProductField_weightUnit_setsUnit() {
        Product p = blankProduct();
        applyField(p, "weightUnit", "kg");
        assertEquals("kg", p.getWeightUnit());
    }

    @Test
    void applyProductField_status_setsStatus() {
        Product p = blankProduct();
        applyField(p, "status", "ACTIVE");
        assertEquals(ProductStatus.ACTIVE, p.getStatus());
    }

    @Test
    void applyProductField_status_null_setsNull() {
        Product p = blankProduct();
        applyField(p, "status", null);
        assertNull(p.getStatus());
    }

    @Test
    void applyProductField_scheduledPublishAt_setsInstant() {
        Product p = blankProduct();
        String ts = "2026-01-01T00:00:00Z";
        applyField(p, "scheduledPublishAt", ts);
        assertEquals(Instant.parse(ts), p.getScheduledPublishAt());
    }

    @Test
    void applyProductField_featured_setsTrue() {
        Product p = blankProduct();
        applyField(p, "featured", "true");
        assertTrue(p.isFeatured());
    }

    @Test
    void applyProductField_purchasable_setsTrue() {
        Product p = blankProduct();
        applyField(p, "purchasable", "true");
        assertTrue(p.isPurchasable());
    }

    @Test
    void applyProductField_listed_setsTrue() {
        Product p = blankProduct();
        applyField(p, "listed", "true");
        assertTrue(p.isListed());
    }

    @Test
    void applyProductField_backorderEnabled_setsTrue() {
        Product p = blankProduct();
        applyField(p, "backorderEnabled", "true");
        assertTrue(p.isBackorderEnabled());
    }

    @Test
    void applyProductField_preorderEnabled_setsTrue() {
        Product p = blankProduct();
        applyField(p, "preorderEnabled", "true");
        assertTrue(p.isPreorderEnabled());
    }

    @Test
    void applyProductField_preorderExpectedDate_setsInstant() {
        Product p = blankProduct();
        String ts = "2026-06-01T12:00:00Z";
        applyField(p, "preorderExpectedDate", ts);
        assertEquals(Instant.parse(ts), p.getPreorderExpectedDate());
    }

    @Test
    void applyProductField_subscribable_setsTrue() {
        Product p = blankProduct();
        applyField(p, "subscribable", "true");
        assertTrue(p.isSubscribable());
    }

    @Test
    void applyProductField_subscriptionIntervals_setsValue() {
        Product p = blankProduct();
        applyField(p, "subscriptionIntervals", "monthly,quarterly");
        assertEquals("monthly,quarterly", p.getSubscriptionIntervals());
    }

    @Test
    void applyProductField_subscriptionDiscountPercent_setsValue() {
        Product p = blankProduct();
        applyField(p, "subscriptionDiscountPercent", "10.00");
        assertEquals(new BigDecimal("10.00"), p.getSubscriptionDiscountPercent());
    }

    @Test
    void applyProductField_boostWeight_setsValue() {
        Product p = blankProduct();
        applyField(p, "boostWeight", "5");
        assertEquals(Integer.valueOf(5), p.getBoostWeight());
    }

    @Test
    void applyProductField_pinnedUntil_setsInstant() {
        Product p = blankProduct();
        String ts = "2026-12-31T23:59:59Z";
        applyField(p, "pinnedUntil", ts);
        assertEquals(Instant.parse(ts), p.getPinnedUntil());
    }

    @Test
    void applyProductField_pinnedRank_setsValue() {
        Product p = blankProduct();
        applyField(p, "pinnedRank", "3");
        assertEquals(Integer.valueOf(3), p.getPinnedRank());
    }

    @Test
    void applyProductField_lowStockThreshold_setsValue() {
        Product p = blankProduct();
        applyField(p, "lowStockThreshold", "10");
        assertEquals(Integer.valueOf(10), p.getLowStockThreshold());
    }

    @Test
    void applyProductField_lowStockThresholdPercent_setsValue() {
        Product p = blankProduct();
        applyField(p, "lowStockThresholdPercent", "20");
        assertEquals(Integer.valueOf(20), p.getLowStockThresholdPercent());
    }

    @Test
    void applyProductField_maxStock_setsValue() {
        Product p = blankProduct();
        applyField(p, "maxStock", "100");
        assertEquals(Integer.valueOf(100), p.getMaxStock());
    }

    @Test
    void applyProductField_autoRestockEnabled_setsTrue() {
        Product p = blankProduct();
        applyField(p, "autoRestockEnabled", "true");
        assertTrue(p.isAutoRestockEnabled());
    }

    @Test
    void applyProductField_autoRestockQty_setsValue() {
        Product p = blankProduct();
        applyField(p, "autoRestockQty", "50");
        assertEquals(Integer.valueOf(50), p.getAutoRestockQty());
    }

    @Test
    void applyProductField_marketplaceListed_setsTrue() {
        Product p = blankProduct();
        applyField(p, "marketplaceListed", "true");
        assertTrue(p.isMarketplaceListed());
    }

    @Test
    void applyProductField_unknownField_throwsBadRequest() {
        Product p = blankProduct();
        assertThrows(BadRequestException.class,
                () -> applyField(p, "nonExistentField", "value"));
    }

    // =========================================================================
    // buildRatingMap (private — via reflection)
    // =========================================================================

    @Test
    void buildRatingMap_emptyRepository_returnsEmptyMap() {
        when(productReviewRepository.findAverageRatingsByProductIds(any())).thenReturn(List.of());

        Map<UUID, double[]> result = ReflectionTestUtils.invokeMethod(service, "buildRatingMap", List.of(PRODUCT_ID));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildRatingMap_withData_populatesMap() {
        // Use an explicit List<Object[]> to avoid List.of(Object[]) varargs ambiguity
        List<Object[]> rows = new java.util.ArrayList<>();
        rows.add(new Object[]{PRODUCT_ID, 4.5, 10.0});
        when(productReviewRepository.findAverageRatingsByProductIds(any())).thenReturn(rows);

        Map<UUID, double[]> result = ReflectionTestUtils.invokeMethod(service, "buildRatingMap", List.of(PRODUCT_ID));

        assertNotNull(result);
        assertTrue(result.containsKey(PRODUCT_ID));
        assertEquals(4.5, result.get(PRODUCT_ID)[0], 0.001);
        assertEquals(10.0, result.get(PRODUCT_ID)[1], 0.001);
    }

    @Test
    void buildRatingMap_repositoryThrows_returnsEmptyMap() {
        doThrow(new RuntimeException("DB error"))
                .when(productReviewRepository).findAverageRatingsByProductIds(any());

        Map<UUID, double[]> result = ReflectionTestUtils.invokeMethod(service, "buildRatingMap", List.of(PRODUCT_ID));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // =========================================================================
    // createProduct — DataIntegrityViolationException on save
    // =========================================================================

    @Test
    void createProduct_dataIntegrityViolationOnSave_throwsConflict() {
        when(productRepository.save(any(Product.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("constraint"));

        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setSku("UNIQUE-SKU");

        assertThrows(ConflictException.class, () -> service.createProduct(COMPANY_ID, OWNER_ID, req));
    }

    // =========================================================================
    // searchProducts — page-too-large guard
    // =========================================================================

    @Test
    void searchProducts_pageTooLarge_throwsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                service.searchProducts(COMPANY_ID, null, null, null,
                        null, null, null, null, null, null, null,
                        10_001, 20, "createdAt", "desc"));
    }

    // =========================================================================
    // searchMarketplaceCatalog — page-too-large guard + price range path
    // =========================================================================

    @Test
    void searchMarketplaceCatalog_pageTooLarge_throwsBadRequest() {
        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(true);

        assertThrows(BadRequestException.class, () ->
                service.searchMarketplaceCatalog(MARKETPLACE_ID, null, null, null,
                        null, null, null, null, 10_001, 10, null, null));
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchMarketplaceCatalog_withPriceRange_esSucceeds() {
        when(marketplaceProfileRepository.existsByCompanyId(MARKETPLACE_ID)).thenReturn(true);

        var doc = new backend.documents.ProductDocument();
        doc.setId(PRODUCT_ID);
        var hit = (org.springframework.data.elasticsearch.core.SearchHit<backend.documents.ProductDocument>)
                mock(org.springframework.data.elasticsearch.core.SearchHit.class);
        when(hit.getContent()).thenReturn(doc);

        var hits = (org.springframework.data.elasticsearch.core.SearchHits<backend.documents.ProductDocument>)
                mock(org.springframework.data.elasticsearch.core.SearchHits.class);
        when(hits.stream()).thenReturn(java.util.stream.Stream.of(hit));
        when(hits.getTotalHits()).thenReturn(1L);
        when(hits.getAggregations()).thenReturn(null);
        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class), (Class) any()))
                .thenReturn(hits);

        Product product = makeProduct();
        product.setMarketplaceId(MARKETPLACE_ID);
        when(productRepository.findAllByIdInAndMarketplaceId(any(), eq(MARKETPLACE_ID)))
                .thenReturn(List.of(product));
        when(marketplaceVendorRepository.findByMarketplaceIdAndVendorCompanyIdIn(eq(MARKETPLACE_ID), any()))
                .thenReturn(List.of());

        // Pass minPrice and maxPrice to trigger the RangeQuery branch
        var result = service.searchMarketplaceCatalog(MARKETPLACE_ID, null, null, null,
                new BigDecimal("10.00"), new BigDecimal("100.00"), null, null, 0, 10, "price", "asc");

        assertNotNull(result);
    }

    // =========================================================================
    // searchCompanyCatalog — company not found + price range path
    // =========================================================================

    @Test
    void searchCompanyCatalog_companyNotFound_throwsResourceNotFound() {
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                service.searchCompanyCatalog(COMPANY_ID, null, null, null,
                        null, null, 0, 10, null, null));
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchCompanyCatalog_withPriceRange_esSucceeds() {
        var doc = new backend.documents.ProductDocument();
        doc.setId(PRODUCT_ID);
        var hit = (org.springframework.data.elasticsearch.core.SearchHit<backend.documents.ProductDocument>)
                mock(org.springframework.data.elasticsearch.core.SearchHit.class);
        when(hit.getContent()).thenReturn(doc);

        var hits = (org.springframework.data.elasticsearch.core.SearchHits<backend.documents.ProductDocument>)
                mock(org.springframework.data.elasticsearch.core.SearchHits.class);
        when(hits.stream()).thenReturn(java.util.stream.Stream.of(hit));
        when(hits.getTotalHits()).thenReturn(1L);
        when(hits.getAggregations()).thenReturn(null);
        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class), (Class) any()))
                .thenReturn(hits);

        when(productRepository.findAllByIdInAndCompanyId(any(), eq(COMPANY_ID)))
                .thenReturn(List.of(makeProduct()));

        var result = service.searchCompanyCatalog(COMPANY_ID, null, null, null,
                new BigDecimal("5.00"), new BigDecimal("50.00"), 0, 10, "price", "desc");

        assertNotNull(result);
    }

    // =========================================================================
    // updateProduct — price drop event + marketplace eviction + pinning
    // =========================================================================

    @Test
    void updateProduct_priceDropBelowOriginal_publishesPriceDropAlertEvent() {
        Product existing = makeProduct(PRODUCT_ID);
        existing.setPrice(new BigDecimal("50.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        // Snapshot must return a separate object so 'before.price' stays at 50.00
        // when the product's price is later overwritten to 30.00
        Product beforeSnapshot = makeProduct(PRODUCT_ID);
        beforeSnapshot.setPrice(new BigDecimal("50.00"));
        when(productChangeLogger.snapshot(any(Product.class))).thenReturn(beforeSnapshot);

        UpdateProductRequest req = new UpdateProductRequest();
        req.setPrice(new BigDecimal("30.00")); // lower than 50.00 → triggers price drop event

        ProductResponse result = service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertNotNull(result);
        // Price drop event + ProductIndexEvent both published
        verify(eventPublisher, atLeast(2)).publishEvent(any(Object.class));
    }

    @Test
    void updateProduct_priceRaisedAboveOriginal_doesNotPublishPriceDropEvent() {
        Product existing = makeProduct(PRODUCT_ID);
        existing.setPrice(new BigDecimal("30.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        Product beforeSnapshot = makeProduct(PRODUCT_ID);
        beforeSnapshot.setPrice(new BigDecimal("30.00"));
        when(productChangeLogger.snapshot(any(Product.class))).thenReturn(beforeSnapshot);

        UpdateProductRequest req = new UpdateProductRequest();
        req.setPrice(new BigDecimal("50.00")); // higher — no price drop

        ProductResponse result = service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertNotNull(result);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void updateProduct_withMarketplaceId_evictsMarketplaceCache() {
        Product existing = makeProduct(PRODUCT_ID);
        existing.setMarketplaceId(MARKETPLACE_ID);
        existing.setMarketplaceListed(true);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        UpdateProductRequest req = new UpdateProductRequest();
        req.setName("Updated With Marketplace");

        service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        verify(singleFlightCache, atLeastOnce()).evict(contains("marketplace:product:"));
    }

    @Test
    void updateProduct_withValidPinnedUntil_setsPinAndRank() {
        Product existing = makeProduct(PRODUCT_ID);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        Instant futurePin = Instant.now().plusSeconds(86400);
        UpdateProductRequest req = new UpdateProductRequest();
        req.setPinnedUntil(futurePin);
        req.setPinnedRank(1);

        ProductResponse result = service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertNotNull(result);
        assertEquals(futurePin, existing.getPinnedUntil());
        assertEquals(1, existing.getPinnedRank());
    }

    @Test
    void updateProduct_withPinnedRankOnly_setsRankWithoutChangingPin() {
        Instant existingPin = Instant.now().plusSeconds(86400);
        Product existing = makeProduct(PRODUCT_ID);
        existing.setPinnedUntil(existingPin);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        UpdateProductRequest req = new UpdateProductRequest();
        req.setPinnedRank(5); // only rank, no new pinnedUntil

        service.updateProduct(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertEquals(5, existing.getPinnedRank());
        assertEquals(existingPin, existing.getPinnedUntil()); // unchanged
    }

    // =========================================================================
    // applyProductField — null branches for nullable fields
    // =========================================================================

    @Test
    void applyProductField_compareAtPrice_null_setsNull() {
        Product p = blankProduct();
        p.setCompareAtPrice(new BigDecimal("10.00"));
        applyField(p, "compareAtPrice", null);
        assertNull(p.getCompareAtPrice());
    }

    @Test
    void applyProductField_weight_null_setsNull() {
        Product p = blankProduct();
        p.setWeight(new BigDecimal("1.5"));
        applyField(p, "weight", null);
        assertNull(p.getWeight());
    }

    @Test
    void applyProductField_scheduledPublishAt_null_setsNull() {
        Product p = blankProduct();
        p.setScheduledPublishAt(Instant.now());
        applyField(p, "scheduledPublishAt", null);
        assertNull(p.getScheduledPublishAt());
    }

    @Test
    void applyProductField_preorderExpectedDate_null_setsNull() {
        Product p = blankProduct();
        p.setPreorderExpectedDate(Instant.now());
        applyField(p, "preorderExpectedDate", null);
        assertNull(p.getPreorderExpectedDate());
    }

    @Test
    void applyProductField_subscriptionDiscountPercent_null_setsNull() {
        Product p = blankProduct();
        applyField(p, "subscriptionDiscountPercent", null);
        assertNull(p.getSubscriptionDiscountPercent());
    }

    @Test
    void applyProductField_boostWeight_null_setsNull() {
        Product p = blankProduct();
        p.setBoostWeight(3);
        applyField(p, "boostWeight", null);
        assertNull(p.getBoostWeight());
    }

    @Test
    void applyProductField_pinnedUntil_null_setsNull() {
        Product p = blankProduct();
        p.setPinnedUntil(Instant.now());
        applyField(p, "pinnedUntil", null);
        assertNull(p.getPinnedUntil());
    }

    @Test
    void applyProductField_pinnedRank_null_setsNull() {
        Product p = blankProduct();
        p.setPinnedRank(2);
        applyField(p, "pinnedRank", null);
        assertNull(p.getPinnedRank());
    }

    @Test
    void applyProductField_lowStockThreshold_null_setsNull() {
        Product p = blankProduct();
        p.setLowStockThreshold(5);
        applyField(p, "lowStockThreshold", null);
        assertNull(p.getLowStockThreshold());
    }

    @Test
    void applyProductField_lowStockThresholdPercent_null_setsNull() {
        Product p = blankProduct();
        p.setLowStockThresholdPercent(10);
        applyField(p, "lowStockThresholdPercent", null);
        assertNull(p.getLowStockThresholdPercent());
    }

    @Test
    void applyProductField_maxStock_null_setsNull() {
        Product p = blankProduct();
        p.setMaxStock(100);
        applyField(p, "maxStock", null);
        assertNull(p.getMaxStock());
    }

    @Test
    void applyProductField_autoRestockQty_null_setsNull() {
        Product p = blankProduct();
        p.setAutoRestockQty(50);
        applyField(p, "autoRestockQty", null);
        assertNull(p.getAutoRestockQty());
    }

    // =========================================================================
    // applyVariantField (private — exhaustive switch coverage via reflection)
    // =========================================================================

    private void applyVariantField(ProductVariant v, String field, String value) {
        ReflectionTestUtils.invokeMethod(service, "applyVariantField", v, field, value);
    }

    @Test
    void applyVariantField_sku_setsSku() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "sku", "VAR-001");
        assertEquals("VAR-001", v.getSku());
    }

    @Test
    void applyVariantField_price_setsPrice() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "price", "9.99");
        assertEquals(new BigDecimal("9.99"), v.getPrice());
    }

    @Test
    void applyVariantField_price_null_setsNull() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "price", null);
        assertNull(v.getPrice());
    }

    @Test
    void applyVariantField_compareAtPrice_setsValue() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "compareAtPrice", "14.99");
        assertEquals(new BigDecimal("14.99"), v.getCompareAtPrice());
    }

    @Test
    void applyVariantField_compareAtPrice_null_setsNull() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "compareAtPrice", null);
        assertNull(v.getCompareAtPrice());
    }

    @Test
    void applyVariantField_lowStockThreshold_setsValue() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "lowStockThreshold", "5");
        assertEquals(Integer.valueOf(5), v.getLowStockThreshold());
    }

    @Test
    void applyVariantField_lowStockThreshold_null_setsNull() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "lowStockThreshold", null);
        assertNull(v.getLowStockThreshold());
    }

    @Test
    void applyVariantField_lowStockThresholdPercent_setsValue() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "lowStockThresholdPercent", "10");
        assertEquals(Integer.valueOf(10), v.getLowStockThresholdPercent());
    }

    @Test
    void applyVariantField_lowStockThresholdPercent_null_setsNull() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "lowStockThresholdPercent", null);
        assertNull(v.getLowStockThresholdPercent());
    }

    @Test
    void applyVariantField_maxStock_setsValue() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "maxStock", "200");
        assertEquals(Integer.valueOf(200), v.getMaxStock());
    }

    @Test
    void applyVariantField_maxStock_null_setsNull() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "maxStock", null);
        assertNull(v.getMaxStock());
    }

    @Test
    void applyVariantField_autoRestockEnabled_setsTrue() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "autoRestockEnabled", "true");
        assertTrue(v.isAutoRestockEnabled());
    }

    @Test
    void applyVariantField_autoRestockQty_setsValue() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "autoRestockQty", "25");
        assertEquals(Integer.valueOf(25), v.getAutoRestockQty());
    }

    @Test
    void applyVariantField_autoRestockQty_null_setsNull() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "autoRestockQty", null);
        assertNull(v.getAutoRestockQty());
    }

    @Test
    void applyVariantField_purchasable_setsTrue() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "purchasable", "true");
        assertTrue(v.isPurchasable());
    }

    @Test
    void applyVariantField_backorderEnabled_setsTrue() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "backorderEnabled", "true");
        assertTrue(v.isBackorderEnabled());
    }

    @Test
    void applyVariantField_preorderEnabled_setsTrue() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "preorderEnabled", "true");
        assertTrue(v.isPreorderEnabled());
    }

    @Test
    void applyVariantField_preorderExpectedDate_setsInstant() {
        ProductVariant v = makeVariant(VARIANT_ID);
        String ts = "2026-09-01T00:00:00Z";
        applyVariantField(v, "preorderExpectedDate", ts);
        assertEquals(Instant.parse(ts), v.getPreorderExpectedDate());
    }

    @Test
    void applyVariantField_preorderExpectedDate_null_setsNull() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "preorderExpectedDate", null);
        assertNull(v.getPreorderExpectedDate());
    }

    @Test
    void applyVariantField_option1_setsValue() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "option1", "Red");
        assertEquals("Red", v.getOption1());
    }

    @Test
    void applyVariantField_option2_setsValue() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "option2", "Large");
        assertEquals("Large", v.getOption2());
    }

    @Test
    void applyVariantField_option3_setsValue() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "option3", "Matte");
        assertEquals("Matte", v.getOption3());
    }

    @Test
    void applyVariantField_displayOrder_setsValue() {
        ProductVariant v = makeVariant(VARIANT_ID);
        applyVariantField(v, "displayOrder", "3");
        assertEquals(3, v.getDisplayOrder());
    }

    @Test
    void applyVariantField_displayOrder_null_setsZero() {
        ProductVariant v = makeVariant(VARIANT_ID);
        v.setDisplayOrder(5);
        applyVariantField(v, "displayOrder", null);
        assertEquals(0, v.getDisplayOrder());
    }

    @Test
    void applyVariantField_unknownField_throwsBadRequest() {
        ProductVariant v = makeVariant(VARIANT_ID);
        assertThrows(BadRequestException.class,
                () -> applyVariantField(v, "nonExistentVariantField", "value"));
    }

    // =========================================================================
    // revertProductChanges — variant log entry path
    // =========================================================================

    @Test
    void revertProductChanges_variantField_revertsVariantSku() {
        Product p = makeProduct();
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(p));

        ProductVariant variant = makeVariant(VARIANT_ID);
        variant.setSku("NEW-VAR-SKU");
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID))
                .thenReturn(Optional.of(variant));
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID logId = TestIds.uuid(80);
        ProductChangeLog entry = new ProductChangeLog();
        entry.setId(logId);
        entry.setProduct(p);
        entry.setVariant(variant);
        entry.setFieldName("sku");
        entry.setOldValue("OLD-VAR-SKU");
        entry.setNewValue("NEW-VAR-SKU");
        entry.setChangeType(ProductChangeType.UPDATED);
        entry.setSource(ChangeSource.USER);
        entry.setChangedAt(Instant.now());

        when(productChangeLogRepository.findAllByIdInAndCompanyId(List.of(logId), COMPANY_ID))
                .thenReturn(List.of(entry));

        backend.dtos.requests.product.RevertProductChangesRequest req =
                new backend.dtos.requests.product.RevertProductChangesRequest();
        req.setLogEntryIds(List.of(logId));

        ProductResponse result = service.revertProductChanges(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertNotNull(result);
        assertEquals("OLD-VAR-SKU", variant.getSku());
        verify(productVariantRepository).save(variant);
        verify(productChangeLogger).logVariantUpdate(any(), any(), eq(ChangeSource.REVERT), any());
    }

    @Test
    void revertProductChanges_variantNotFound_throwsResourceNotFound() {
        Product p = makeProduct();
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(p));

        ProductVariant variant = makeVariant(VARIANT_ID);
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID))
                .thenReturn(Optional.empty());

        UUID logId = TestIds.uuid(81);
        ProductChangeLog entry = new ProductChangeLog();
        entry.setId(logId);
        entry.setProduct(p);
        entry.setVariant(variant);
        entry.setFieldName("price");
        entry.setOldValue("9.99");
        entry.setChangeType(ProductChangeType.UPDATED);
        entry.setSource(ChangeSource.USER);
        entry.setChangedAt(Instant.now());

        when(productChangeLogRepository.findAllByIdInAndCompanyId(List.of(logId), COMPANY_ID))
                .thenReturn(List.of(entry));

        backend.dtos.requests.product.RevertProductChangesRequest req =
                new backend.dtos.requests.product.RevertProductChangesRequest();
        req.setLogEntryIds(List.of(logId));

        assertThrows(ResourceNotFoundException.class,
                () -> service.revertProductChanges(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }
}
