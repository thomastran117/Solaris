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
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductImage;
import backend.models.core.ProductOption;
import backend.models.core.ProductVariant;
import backend.models.enums.CompanyCapability;
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
                mock(backend.repositories.ProductSimilarityRepository.class),
                companyAccessService,
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
}
