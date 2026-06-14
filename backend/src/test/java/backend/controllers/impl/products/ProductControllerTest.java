package backend.controllers.impl.products;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.requests.product.AddProductImageRequest;
import backend.dtos.requests.product.BatchCreateProductsRequest;
import backend.dtos.requests.product.BatchDeleteProductsRequest;
import backend.dtos.requests.product.BatchUpdateProductsRequest;
import backend.dtos.requests.product.CreateProductOptionRequest;
import backend.dtos.requests.product.CreateProductRequest;
import backend.dtos.requests.product.CreateProductVariantRequest;
import backend.dtos.requests.product.ReorderProductImagesRequest;
import backend.dtos.requests.product.SetProductAttributesRequest;
import backend.dtos.requests.product.UpdateProductRequest;
import backend.dtos.requests.product.UpdateProductVariantRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.ProductImageResponse;
import backend.dtos.responses.product.ProductOptionResponse;
import backend.dtos.responses.product.ProductResponse;
import backend.dtos.responses.product.ProductVariantResponse;
import backend.dtos.responses.product.SimilarProductResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.kafka.workers.ProductIndexingService;
import backend.models.enums.CompanyRole;
import backend.services.intf.SanitizationService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.products.BundleService;
import backend.services.intf.products.ProductService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductControllerTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID USER_ID    = TestIds.uuid(2);
    private static final UUID PRODUCT_ID = TestIds.uuid(3);
    private static final UUID IMAGE_ID   = TestIds.uuid(4);
    private static final UUID OPTION_ID  = TestIds.uuid(5);
    private static final UUID VARIANT_ID = TestIds.uuid(6);
    private static final UUID TARGET_ID  = TestIds.uuid(7);

    private ProductService productService;
    private BundleService bundleService;
    private ProductIndexingService productIndexingService;
    private SanitizationService sanitizationService;
    private CompanyAccessService companyAccessService;
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        productService        = mock(ProductService.class);
        bundleService         = mock(BundleService.class);
        productIndexingService = mock(ProductIndexingService.class);
        sanitizationService   = mock(SanitizationService.class);
        companyAccessService  = mock(CompanyAccessService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ProductController(productService, bundleService,
                                productIndexingService, sanitizationService, companyAccessService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();

        // Default: not a company member
        when(companyAccessService.resolveRole(any(), any())).thenReturn(Optional.empty());
        // Default: products are publicly visible (ACTIVE + listed) so public child-read endpoints
        // resolve; individual tests override this to exercise the hidden-product 404 path.
        when(productService.isPubliclyVisible(any(), any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET /companies/{companyId}/products ──────────────────────────────────

    @Test
    void getProducts_returns200() throws Exception {
        when(productService.searchProducts(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        mockMvc.perform(get("/companies/{cid}/products", COMPANY_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getProducts_unauthenticated_forcesActiveStatus() throws Exception {
        when(productService.searchProducts(eq(COMPANY_ID), any(), any(), any(), any(), any(), any(),
                eq(backend.models.enums.ProductStatus.ACTIVE),
                any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        // No authentication set — should enforce ACTIVE status
        mockMvc.perform(get("/companies/{cid}/products", COMPANY_ID)
                        .param("status", "DRAFT"))
                .andExpect(status().isOk());

        verify(productService).searchProducts(eq(COMPANY_ID), any(), any(), any(), any(), any(), any(),
                eq(backend.models.enums.ProductStatus.ACTIVE),
                any(), any(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void getProducts_authenticatedMember_passesRequestedStatus() throws Exception {
        authenticateAs(USER_ID);
        when(companyAccessService.resolveRole(COMPANY_ID, USER_ID)).thenReturn(Optional.of(CompanyRole.OWNER));
        when(productService.searchProducts(eq(COMPANY_ID), any(), any(), any(), any(), any(), any(),
                eq(backend.models.enums.ProductStatus.DRAFT),
                any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        mockMvc.perform(get("/companies/{cid}/products", COMPANY_ID).param("status", "DRAFT"))
                .andExpect(status().isOk());

        verify(productService).searchProducts(eq(COMPANY_ID), any(), any(), any(), any(), any(), any(),
                eq(backend.models.enums.ProductStatus.DRAFT),
                any(), any(), any(), anyInt(), anyInt(), any(), any());
    }

    // ─── GET /companies/{companyId}/products/{id} ─────────────────────────────

    @Test
    void getProduct_activeProduct_returns200() throws Exception {
        when(productService.getProduct(COMPANY_ID, PRODUCT_ID)).thenReturn(makeProductResponse("ACTIVE"));

        mockMvc.perform(get("/companies/{cid}/products/{id}", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()));
    }

    @Test
    void getProduct_draftProduct_unauthenticated_returns404() throws Exception {
        when(productService.getProduct(COMPANY_ID, PRODUCT_ID)).thenReturn(makeProductResponse("DRAFT"));

        mockMvc.perform(get("/companies/{cid}/products/{id}", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProduct_draftProduct_memberAuthenticated_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(companyAccessService.resolveRole(COMPANY_ID, USER_ID)).thenReturn(Optional.of(CompanyRole.OWNER));
        when(productService.getProduct(COMPANY_ID, PRODUCT_ID)).thenReturn(makeProductResponse("DRAFT"));

        mockMvc.perform(get("/companies/{cid}/products/{id}", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getProduct_notFound_returns404() throws Exception {
        when(productService.getProduct(COMPANY_ID, PRODUCT_ID))
                .thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/companies/{cid}/products/{id}", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isNotFound());
    }

    // ─── POST /companies/{companyId}/products ─────────────────────────────────

    @Test
    void createProduct_returns201WithBody() throws Exception {
        authenticateAs(USER_ID);
        when(productService.createProduct(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(makeProductResponse("DRAFT"));

        mockMvc.perform(post("/companies/{cid}/products", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalCreateProductRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()));
    }

    @Test
    void createProduct_conflict_returns409() throws Exception {
        authenticateAs(USER_ID);
        when(productService.createProduct(any(), any(), any()))
                .thenThrow(new ConflictException("SKU exists"));

        mockMvc.perform(post("/companies/{cid}/products", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalCreateProductRequest())))
                .andExpect(status().isConflict());
    }

    // ─── PATCH /companies/{companyId}/products/{id} ───────────────────────────

    @Test
    void updateProduct_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(productService.updateProduct(eq(COMPANY_ID), eq(PRODUCT_ID), eq(USER_ID), any()))
                .thenReturn(makeProductResponse("ACTIVE"));

        mockMvc.perform(patch("/companies/{cid}/products/{id}", COMPANY_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateProduct_badRequest_returns400() throws Exception {
        authenticateAs(USER_ID);
        when(productService.updateProduct(any(), any(), any(), any()))
                .thenThrow(new BadRequestException("Invalid status"));

        mockMvc.perform(patch("/companies/{cid}/products/{id}", COMPANY_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProduct_notFound_returns404() throws Exception {
        authenticateAs(USER_ID);
        when(productService.updateProduct(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(patch("/companies/{cid}/products/{id}", COMPANY_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /companies/{companyId}/products/{id} ──────────────────────────

    @Test
    void deleteProduct_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/companies/{cid}/products/{id}", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(COMPANY_ID, PRODUCT_ID, USER_ID);
    }

    @Test
    void deleteProduct_inBundle_returns409() throws Exception {
        authenticateAs(USER_ID);
        doThrow(new ConflictException("in bundle"))
                .when(productService).deleteProduct(any(), any(), any());

        mockMvc.perform(delete("/companies/{cid}/products/{id}", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isConflict());
    }

    // ─── POST /companies/{companyId}/products/batch-create ────────────────────

    @Test
    void batchCreateProducts_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(productService.batchCreateProducts(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(List.of(makeProductResponse("DRAFT")));

        BatchCreateProductsRequest req = new BatchCreateProductsRequest();
        req.setProducts(List.of(minimalCreateProductRequest()));

        mockMvc.perform(post("/companies/{cid}/products/batch-create", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    // ─── POST /companies/{companyId}/products/batch-delete ────────────────────

    @Test
    void batchDeleteProducts_returns204() throws Exception {
        authenticateAs(USER_ID);

        BatchDeleteProductsRequest req = new BatchDeleteProductsRequest();
        req.setIds(List.of(PRODUCT_ID));

        mockMvc.perform(post("/companies/{cid}/products/batch-delete", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    // ─── POST /companies/{companyId}/products/{productId}/images ──────────────

    @Test
    void addProductImage_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(productService.addProductImage(eq(COMPANY_ID), eq(PRODUCT_ID), eq(USER_ID), any()))
                .thenReturn(new ProductImageResponse(IMAGE_ID, "https://example.com/img.jpg", 0, Instant.now()));

        AddProductImageRequest req = new AddProductImageRequest();
        req.setImageUrl("https://example.com/img.jpg");

        mockMvc.perform(post("/companies/{cid}/products/{pid}/images", COMPANY_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/img.jpg"));
    }

    @Test
    void addProductImage_limitExceeded_returns400() throws Exception {
        authenticateAs(USER_ID);
        when(productService.addProductImage(any(), any(), any(), any()))
                .thenThrow(new BadRequestException("max 5 images"));

        AddProductImageRequest req = new AddProductImageRequest();
        req.setImageUrl("https://example.com/img.jpg");

        mockMvc.perform(post("/companies/{cid}/products/{pid}/images", COMPANY_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─── DELETE /companies/{companyId}/products/{productId}/images/{imageId} ──

    @Test
    void deleteProductImage_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/companies/{cid}/products/{pid}/images/{imgId}",
                        COMPANY_ID, PRODUCT_ID, IMAGE_ID))
                .andExpect(status().isNoContent());

        verify(productService).deleteProductImage(COMPANY_ID, PRODUCT_ID, IMAGE_ID, USER_ID);
    }

    // ─── PATCH /companies/{companyId}/products/{productId}/images/reorder ─────

    @Test
    void reorderProductImages_mismatchCount_returns400() throws Exception {
        authenticateAs(USER_ID);
        when(productService.reorderProductImages(any(), any(), any(), any()))
                .thenThrow(new BadRequestException("count mismatch"));

        ReorderProductImagesRequest req = new ReorderProductImagesRequest();
        req.setImageIds(List.of(IMAGE_ID));

        mockMvc.perform(patch("/companies/{cid}/products/{pid}/images/reorder", COMPANY_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /companies/{companyId}/products/{productId}/options ─────────────

    @Test
    void addProductOption_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(productService.addProductOption(eq(COMPANY_ID), eq(PRODUCT_ID), eq(USER_ID), any()))
                .thenReturn(new ProductOptionResponse(OPTION_ID, "Size", 0));

        CreateProductOptionRequest req = new CreateProductOptionRequest();
        req.setName("Size");

        mockMvc.perform(post("/companies/{cid}/products/{pid}/options", COMPANY_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Size"));
    }

    @Test
    void addProductOption_limitExceeded_returns400() throws Exception {
        authenticateAs(USER_ID);
        when(productService.addProductOption(any(), any(), any(), any()))
                .thenThrow(new BadRequestException("max 3 options"));

        CreateProductOptionRequest req = new CreateProductOptionRequest();
        req.setName("Size");

        mockMvc.perform(post("/companies/{cid}/products/{pid}/options", COMPANY_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─── DELETE /companies/{companyId}/products/{productId}/options/{optionId} -

    @Test
    void deleteProductOption_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/companies/{cid}/products/{pid}/options/{oid}",
                        COMPANY_ID, PRODUCT_ID, OPTION_ID))
                .andExpect(status().isNoContent());

        verify(productService).deleteProductOption(COMPANY_ID, PRODUCT_ID, OPTION_ID, USER_ID);
    }

    // ─── POST /companies/{companyId}/products/{productId}/variants ────────────

    @Test
    void createProductVariant_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(productService.createProductVariant(eq(COMPANY_ID), eq(PRODUCT_ID), eq(USER_ID), any()))
                .thenReturn(makeVariantResponse());

        CreateProductVariantRequest req = new CreateProductVariantRequest();
        req.setPrice(new BigDecimal("14.99"));

        mockMvc.perform(post("/companies/{cid}/products/{pid}/variants", COMPANY_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    // ─── POST /companies/{companyId}/products/batch-update ────────────────────

    @Test
    void batchUpdateProducts_returns200WithResults() throws Exception {
        authenticateAs(USER_ID);
        when(productService.batchUpdateProducts(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(List.of(makeProductResponse("ACTIVE")));

        BatchUpdateProductsRequest req = new BatchUpdateProductsRequest();
        req.setIds(List.of(PRODUCT_ID));
        req.setStatus(backend.models.enums.ProductStatus.ACTIVE);

        mockMvc.perform(post("/companies/{cid}/products/batch-update", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(PRODUCT_ID.toString()));
    }

    @Test
    void batchUpdateProducts_scheduledStatus_returns400() throws Exception {
        authenticateAs(USER_ID);
        when(productService.batchUpdateProducts(any(), any(), any()))
                .thenThrow(new BadRequestException("SCHEDULED not allowed in bulk"));

        BatchUpdateProductsRequest req = new BatchUpdateProductsRequest();
        req.setIds(List.of(PRODUCT_ID));
        req.setStatus(backend.models.enums.ProductStatus.SCHEDULED);

        mockMvc.perform(post("/companies/{cid}/products/batch-update", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /companies/{companyId}/products/{productId}/duplicate ───────────

    @Test
    void duplicateProduct_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(productService.duplicateProduct(eq(COMPANY_ID), eq(PRODUCT_ID), eq(USER_ID)))
                .thenReturn(makeProductResponse("DRAFT"));

        mockMvc.perform(post("/companies/{cid}/products/{pid}/duplicate", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()));
    }

    // ─── DELETE /companies/{companyId}/products/{productId}/variants/{variantId}

    @Test
    void deleteProductVariant_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/companies/{cid}/products/{pid}/variants/{vid}",
                        COMPANY_ID, PRODUCT_ID, VARIANT_ID))
                .andExpect(status().isNoContent());

        verify(productService).deleteProductVariant(COMPANY_ID, PRODUCT_ID, VARIANT_ID, USER_ID);
    }

    // ─── GET /{productId}/relationships ──────────────────────────────────────

    @Test
    void getRelationships_returns200() throws Exception {
        when(productService.getProductRelationships(COMPANY_ID, PRODUCT_ID, null))
                .thenReturn(List.of());

        mockMvc.perform(get("/companies/{cid}/products/{pid}/relationships", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void childRead_unauthenticated_hiddenProduct_returns404() throws Exception {
        // Non-member + product not publicly visible (draft/unlisted) → child metadata must be hidden.
        when(productService.isPubliclyVisible(COMPANY_ID, PRODUCT_ID)).thenReturn(false);

        mockMvc.perform(get("/companies/{cid}/products/{pid}/variants", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/companies/{cid}/products/{pid}/images", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isNotFound());
        verify(productService, never()).getProductVariants(any(), any());
        verify(productService, never()).getProductImages(any(), any());
    }

    @Test
    void childRead_member_hiddenProduct_isAllowed() throws Exception {
        // A company member can read child data even for a non-public (draft/unlisted) product.
        authenticateAs(USER_ID);
        when(companyAccessService.resolveRole(COMPANY_ID, USER_ID)).thenReturn(Optional.of(CompanyRole.OWNER));
        when(productService.isPubliclyVisible(COMPANY_ID, PRODUCT_ID)).thenReturn(false);
        when(productService.getProductVariants(COMPANY_ID, PRODUCT_ID)).thenReturn(List.of());

        mockMvc.perform(get("/companies/{cid}/products/{pid}/variants", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getRelationships_withTypeFilter_returnsFilteredList() throws Exception {
        backend.dtos.responses.product.ProductRelationshipResponse rel =
                new backend.dtos.responses.product.ProductRelationshipResponse(
                        TARGET_ID, "Target Product", "SKU-TARGET", null,
                        backend.models.enums.ProductRelationshipType.UPGRADE, null, 0);
        when(productService.getProductRelationships(
                COMPANY_ID, PRODUCT_ID, backend.models.enums.ProductRelationshipType.UPGRADE))
                .thenReturn(List.of(rel));

        mockMvc.perform(get("/companies/{cid}/products/{pid}/relationships", COMPANY_ID, PRODUCT_ID)
                        .param("type", "UPGRADE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetProductId").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$[0].type").value("UPGRADE"));
    }

    @Test
    void getRelationships_productNotFound_returns404() throws Exception {
        when(productService.getProductRelationships(COMPANY_ID, PRODUCT_ID, null))
                .thenThrow(new ResourceNotFoundException("Product not found"));

        mockMvc.perform(get("/companies/{cid}/products/{pid}/relationships", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isNotFound());
    }

    // ─── POST /{productId}/relationships ──────────────────────────────────────

    @Test
    void addRelationship_validRequest_returns201() throws Exception {
        authenticateAs(USER_ID);
        backend.dtos.responses.product.ProductRelationshipResponse resp =
                new backend.dtos.responses.product.ProductRelationshipResponse(
                        TARGET_ID, "Target Product", "SKU-TARGET", null,
                        backend.models.enums.ProductRelationshipType.ACCESSORY, "Compatible case", 0);
        when(productService.addProductRelationship(eq(COMPANY_ID), eq(PRODUCT_ID), eq(USER_ID), any()))
                .thenReturn(resp);

        mockMvc.perform(post("/companies/{cid}/products/{pid}/relationships", COMPANY_ID, PRODUCT_ID)
                        .contentType("application/json")
                        .content("{\"targetProductId\":\"" + TARGET_ID + "\",\"type\":\"ACCESSORY\",\"note\":\"Compatible case\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ACCESSORY"))
                .andExpect(jsonPath("$.note").value("Compatible case"));
    }

    @Test
    void addRelationship_conflict_returns409() throws Exception {
        authenticateAs(USER_ID);
        when(productService.addProductRelationship(eq(COMPANY_ID), eq(PRODUCT_ID), eq(USER_ID), any()))
                .thenThrow(new ConflictException("Relationship already exists"));

        mockMvc.perform(post("/companies/{cid}/products/{pid}/relationships", COMPANY_ID, PRODUCT_ID)
                        .contentType("application/json")
                        .content("{\"targetProductId\":\"" + TARGET_ID + "\",\"type\":\"REPLACEMENT\"}"))
                .andExpect(status().isConflict());
    }

    // ─── DELETE /{productId}/relationships/{targetProductId} ─────────────────

    @Test
    void removeRelationship_returns204() throws Exception {
        authenticateAs(USER_ID);
        doNothing().when(productService).removeProductRelationship(
                eq(COMPANY_ID), eq(PRODUCT_ID), eq(TARGET_ID),
                eq(backend.models.enums.ProductRelationshipType.UPGRADE), eq(USER_ID));

        mockMvc.perform(delete("/companies/{cid}/products/{pid}/relationships/{tid}",
                        COMPANY_ID, PRODUCT_ID, TARGET_ID)
                        .param("type", "UPGRADE"))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeRelationship_notFound_returns404() throws Exception {
        authenticateAs(USER_ID);
        doThrow(new ResourceNotFoundException("Relationship not found"))
                .when(productService).removeProductRelationship(any(), any(), any(), any(), any());

        mockMvc.perform(delete("/companies/{cid}/products/{pid}/relationships/{tid}",
                        COMPANY_ID, PRODUCT_ID, TARGET_ID)
                        .param("type", "ALTERNATIVE"))
                .andExpect(status().isNotFound());
    }

    // ─── similar products ─────────────────────────────────────────────────────

    @Test
    void getSimilarProducts_returns200() throws Exception {
        SimilarProductResponse resp = new SimilarProductResponse(
                TARGET_ID, "Similar Product", "SKU-SIM-001", null,
                new BigDecimal("49.99"), null, "USD", "Electronics", "Acme",
                4.5, 12L, "AUTO", List.of());
        when(productService.getSimilarProducts(COMPANY_ID, PRODUCT_ID, 8))
                .thenReturn(List.of(resp));

        mockMvc.perform(get("/companies/{cid}/products/{pid}/similar", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$[0].source").value("AUTO"));
    }

    @Test
    void getSimilarProducts_withLimit_passesLimitToService() throws Exception {
        when(productService.getSimilarProducts(COMPANY_ID, PRODUCT_ID, 5))
                .thenReturn(List.of());

        mockMvc.perform(get("/companies/{cid}/products/{pid}/similar", COMPANY_ID, PRODUCT_ID)
                        .param("limit", "5"))
                .andExpect(status().isOk());

        verify(productService).getSimilarProducts(COMPANY_ID, PRODUCT_ID, 5);
    }

    @Test
    void getSimilarProducts_productNotFound_returns404() throws Exception {
        when(productService.getSimilarProducts(any(), any(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Product not found"));

        mockMvc.perform(get("/companies/{cid}/products/{pid}/similar", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isNotFound());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private ProductResponse makeProductResponse(String status) {
        return new ProductResponse(
                PRODUCT_ID, COMPANY_ID, "Test Product", null, null,
                new BigDecimal("29.99"), null, "USD", null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null,
                status, null, null, false, true, true, false, null,
                null, null, null, null, null, null, null, null);
    }

    private ProductVariantResponse makeVariantResponse() {
        return new ProductVariantResponse(
                VARIANT_ID, null, new BigDecimal("14.99"), null, null, null,
                false, false, null, null, null, null, null, 0, null, null);
    }

    private CreateProductRequest minimalCreateProductRequest() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        return req;
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
