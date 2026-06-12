package backend.services.impl.imports;

import backend.dtos.requests.imports.AttachImagesRequest;
import backend.dtos.requests.imports.CreateImportJobRequest;
import backend.dtos.requests.inventory.BulkAdjustRequest;
import backend.dtos.requests.product.AddProductImageRequest;
import backend.dtos.requests.product.BatchCreateProductsRequest;
import backend.dtos.requests.product.UpdateProductRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.imports.ImportDownloadResponse;
import backend.dtos.responses.imports.ImportJobResponse;
import backend.dtos.responses.imports.ImportJobRowResponse;
import backend.dtos.responses.product.ProductResponse;
import backend.dtos.responses.upload.PresignUploadResponse;
import backend.events.ImportJobRequestedEvent;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.ImportJob;
import backend.models.core.ImportJobRow;
import backend.models.core.Product;
import backend.models.core.ProductImage;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.CompanyCapability;
import backend.models.enums.ImportJobStatus;
import backend.models.enums.ImportJobType;
import backend.models.enums.ImportMode;
import backend.models.enums.ProductStatus;
import backend.models.enums.UploadFolder;
import backend.repositories.CompanyRepository;
import backend.repositories.ImportJobRepository;
import backend.repositories.ImportJobRowRepository;
import backend.repositories.ProductRepository;
import backend.services.intf.SanitizationService;
import backend.services.intf.StorageService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.inventory.InventoryService;
import backend.services.intf.products.ProductService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportServiceImplTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID OWNER_ID = TestIds.uuid(2);
    private static final UUID JOB_ID = TestIds.uuid(3);
    private static final UUID CREATE_PRODUCT_ID = TestIds.uuid(4);
    private static final UUID UPDATE_PRODUCT_ID = TestIds.uuid(5);

    private CompanyRepository companyRepository;
    private ImportJobRepository jobRepository;
    private ImportJobRowRepository rowRepository;
    private ProductRepository productRepository;
    private ProductService productService;
    private InventoryService inventoryService;
    private CompanyAccessService companyAccessService;
    private SanitizationService sanitizationService;
    private StorageService storageService;
    private ApplicationEventPublisher eventPublisher;
    private ImportServiceImpl service;

    @BeforeEach
    void setUp() {
        companyRepository = mock(CompanyRepository.class);
        jobRepository = mock(ImportJobRepository.class);
        rowRepository = mock(ImportJobRowRepository.class);
        productRepository = mock(ProductRepository.class);
        productService = mock(ProductService.class);
        inventoryService = mock(InventoryService.class);
        companyAccessService = mock(CompanyAccessService.class);
        sanitizationService = mock(SanitizationService.class);
        storageService = mock(StorageService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        when(companyRepository.existsById(COMPANY_ID)).thenReturn(true);

        service = new ImportServiceImpl(
                companyRepository,
                jobRepository,
                rowRepository,
                productRepository,
                productService,
                inventoryService,
                companyAccessService,
                sanitizationService,
                storageService,
                eventPublisher
        );

        when(jobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createJob_inventorySyncRequiresInventoryCapabilityAndPublishesEvent() {
        CreateImportJobRequest request = new CreateImportJobRequest();
        request.setJobType(ImportJobType.INVENTORY_SYNC);
        request.setCsvS3Key("imports/inventory.csv");
        request.setFileName("inventory.csv");

        when(jobRepository.save(any(ImportJob.class))).thenAnswer(inv -> {
            ImportJob job = inv.getArgument(0);
            job.setId(JOB_ID);
            return job;
        });

        ImportJobResponse response = service.createJob(COMPANY_ID, OWNER_ID, request);

        ArgumentCaptor<ImportJob> jobCaptor = ArgumentCaptor.forClass(ImportJob.class);
        verify(jobRepository).save(jobCaptor.capture());
        ImportJob saved = jobCaptor.getValue();
        assertEquals(COMPANY_ID, saved.getCompanyId());
        assertEquals(OWNER_ID, saved.getUploadedBy());
        assertEquals(ImportMode.UPSERT, saved.getMode());
        assertEquals(ImportJobStatus.PENDING, saved.getStatus());
        verify(companyAccessService).require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_INVENTORY);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ImportJobRequestedEvent event = assertInstanceOf(ImportJobRequestedEvent.class, eventCaptor.getValue());
        assertEquals(JOB_ID, event.jobId());
        assertEquals(COMPANY_ID, event.companyId());
        assertEquals(OWNER_ID, event.uploadedBy());
        assertEquals(JOB_ID, response.getId());
    }

    @Test
    void getJob_requiresAccessAndMapsTerminalProgress() {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.COMPLETED);
        job.setErrorReportS3Key("reports/errors.csv");

        when(jobRepository.findByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(Optional.of(job));

        ImportJobResponse response = service.getJob(COMPANY_ID, OWNER_ID, JOB_ID);

        verify(companyAccessService).requireAnyAccess(COMPANY_ID, OWNER_ID);
        assertEquals(100, response.getProgressPercent());
        assertTrue(response.isHasErrorReport());
        assertEquals(ImportJobStatus.COMPLETED, response.getStatus());
    }

    @Test
    void listErrors_returnsPagedRowResponses() {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.COMPLETED_WITH_ERRORS);
        ImportJobRow row = new ImportJobRow();
        row.setId(TestIds.uuid(30));
        row.setJobId(JOB_ID);
        row.setRowNumber(7);
        row.setSku("SKU-7");
        row.setErrorMessage("price is required");

        when(jobRepository.findByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(Optional.of(job));
        when(rowRepository.findAllByJobIdOrderByRowNumberAsc(JOB_ID, PageRequest.of(1, 5)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(1, 5), 6));

        PagedResponse<ImportJobRowResponse> response = service.listErrors(COMPANY_ID, OWNER_ID, JOB_ID, 1, 5);

        verify(companyAccessService).requireAnyAccess(COMPANY_ID, OWNER_ID);
        assertEquals(1, response.getPage());
        assertEquals(1, response.getItems().size());
        assertEquals("SKU-7", response.getItems().get(0).getSku());
    }

    @Test
    void getErrorReport_withoutStoredKeyThrowsNotFound() {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.COMPLETED_WITH_ERRORS);
        when(jobRepository.findByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(Optional.of(job));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getErrorReport(COMPANY_ID, OWNER_ID, JOB_ID));
    }

    @Test
    void exportCatalogue_rejectsCataloguesOverLimit() {
        when(productRepository.countByCompanyId(COMPANY_ID)).thenReturn(50_001L);

        assertThrows(BadRequestException.class, () -> service.exportCatalogue(COMPANY_ID, OWNER_ID));

        verify(companyAccessService).require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_PRODUCTS);
        verify(productRepository, never()).findAllByCompanyId(eq(COMPANY_ID), any(Pageable.class));
    }

    @Test
    void exportCatalogue_writesCsvUploadsItAndReturnsDownloadUrl() {
        Product first = product("SKU-1", "Chair", new BigDecimal("19.99"), 3);
        first.setDescription("Comfortable chair");
        first.setCurrency("CAD");
        first.setStatus(ProductStatus.ACTIVE);
        first.setFeatured(true);
        first.setThumbnailUrl("https://cdn.test/thumb-1.jpg");
        ProductImage image = new ProductImage();
        image.setImageUrl("https://cdn.test/img-1.jpg");
        first.setImages(List.of(image));

        Product second = product("SKU-2", "Lamp", new BigDecimal("49.00"), 8);
        second.setStatus(ProductStatus.DRAFT);

        when(productRepository.countByCompanyId(COMPANY_ID)).thenReturn(2L);
        when(productRepository.findAllByCompanyId(eq(COMPANY_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 1000), 2));
        when(storageService.putBytes(eq(UploadFolder.IMPORT_ERROR_REPORT), eq(OWNER_ID), eq("text/csv"), any(byte[].class)))
                .thenReturn(new PresignUploadResponse("upload", "file", "exports/catalog.csv", 600));
        when(storageService.presignDownload("exports/catalog.csv", 600)).thenReturn("https://download.test/catalog.csv");

        ImportDownloadResponse response = service.exportCatalogue(COMPANY_ID, OWNER_ID);

        ArgumentCaptor<byte[]> csvCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(storageService).putBytes(eq(UploadFolder.IMPORT_ERROR_REPORT), eq(OWNER_ID), eq("text/csv"), csvCaptor.capture());
        String csv = new String(csvCaptor.getValue(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("sku,name,description,price"));
        assertTrue(csv.contains("SKU-1,Chair,Comfortable chair,19.99"));
        assertTrue(csv.contains("https://cdn.test/img-1.jpg"));
        assertEquals("https://download.test/catalog.csv", response.getUrl());
        assertEquals(600, response.getExpiresIn());
    }

    @Test
    void attachImages_normalizesSkuSortsByDisplayOrderAndContinuesAfterFailures() {
        Product product = product("sku-1", "Chair", BigDecimal.TEN, 3);
        product.setId(UPDATE_PRODUCT_ID);

        when(productRepository.findAllBySkuInAndCompanyId(anyCollection(), eq(COMPANY_ID)))
                .thenReturn(List.of(product));
        when(productService.addProductImage(eq(COMPANY_ID), eq(UPDATE_PRODUCT_ID), eq(OWNER_ID), any(AddProductImageRequest.class)))
                .thenAnswer(inv -> {
                    AddProductImageRequest req = inv.getArgument(3);
                    if ("https://cdn.test/fail.jpg".equals(req.getImageUrl())) {
                        throw new RuntimeException("boom");
                    }
                    return null;
                });

        AttachImagesRequest request = new AttachImagesRequest();
        request.setItems(List.of(
                item(" SKU-1 ", "https://cdn.test/ok.jpg", 1),
                item("sku-1", "https://cdn.test/fail.jpg", 0),
                item("missing", "https://cdn.test/missing.jpg", 0)
        ));

        int attached = service.attachImages(COMPANY_ID, OWNER_ID, request);

        ArgumentCaptor<AddProductImageRequest> imageCaptor = ArgumentCaptor.forClass(AddProductImageRequest.class);
        verify(productService, times(2))
                .addProductImage(eq(COMPANY_ID), eq(UPDATE_PRODUCT_ID), eq(OWNER_ID), imageCaptor.capture());
        List<AddProductImageRequest> calls = imageCaptor.getAllValues();
        assertEquals("https://cdn.test/fail.jpg", calls.get(0).getImageUrl());
        assertEquals("https://cdn.test/ok.jpg", calls.get(1).getImageUrl());
        assertEquals(1, attached);
    }

    @Test
    void processJob_whenClaimFailsSkipsProcessing() {
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(0);

        service.processJob(JOB_ID);

        verify(jobRepository, never()).findById(JOB_ID);
        verify(storageService, never()).openObject(anyString());
    }

    @Test
    void processJob_parseFailureMarksJobFailed() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv")).thenThrow(new RuntimeException("broken stream"));

        service.processJob(JOB_ID);

        assertEquals(ImportJobStatus.FAILED, job.getStatus());
        assertTrue(job.getFailureReason().startsWith("Parse failed: broken stream"));
    }

    @Test
    void processJob_productUpsertCreatesUpdatesLogsErrorsAndFinalizes() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        Product existing = product("update-1", "Existing", new BigDecimal("9.99"), 4);
        existing.setId(UPDATE_PRODUCT_ID);

        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv")).thenReturn(csvStream(productUpsertCsv()));
        when(productRepository.findAllBySkuInAndCompanyId(anyCollection(), eq(COMPANY_ID)))
                .thenReturn(List.of(existing));
        when(productService.batchCreateProducts(eq(COMPANY_ID), eq(OWNER_ID), any(BatchCreateProductsRequest.class)))
                .thenReturn(List.of(productResponse(CREATE_PRODUCT_ID, "create-1")));
        when(productService.updateProduct(eq(COMPANY_ID), eq(UPDATE_PRODUCT_ID), eq(OWNER_ID), any(UpdateProductRequest.class)))
                .thenReturn(productResponse(UPDATE_PRODUCT_ID, "update-1"));
        when(storageService.putBytes(eq(UploadFolder.IMPORT_ERROR_REPORT), eq(OWNER_ID), eq("text/csv"), any(byte[].class)))
                .thenReturn(new PresignUploadResponse("upload", "file", "reports/product-errors.csv", 600));

        service.processJob(JOB_ID);

        assertEquals(ImportJobStatus.COMPLETED_WITH_ERRORS, job.getStatus());
        assertEquals("reports/product-errors.csv", job.getErrorReportS3Key());
        assertEquals(3, job.getTotalRows());
        assertEquals(3, job.getProcessedRows());
        assertEquals(2, job.getSuccessRows());
        assertEquals(1, job.getErrorRows());

        ArgumentCaptor<BatchCreateProductsRequest> createCaptor = ArgumentCaptor.forClass(BatchCreateProductsRequest.class);
        verify(productService).batchCreateProducts(eq(COMPANY_ID), eq(OWNER_ID), createCaptor.capture());
        assertEquals(1, createCaptor.getValue().getProducts().size());
        assertEquals("create-1", createCaptor.getValue().getProducts().get(0).getSku());
        assertEquals(new BigDecimal("11.50"), createCaptor.getValue().getProducts().get(0).getPrice());

        ArgumentCaptor<UpdateProductRequest> updateCaptor = ArgumentCaptor.forClass(UpdateProductRequest.class);
        verify(productService).updateProduct(eq(COMPANY_ID), eq(UPDATE_PRODUCT_ID), eq(OWNER_ID), updateCaptor.capture());
        assertEquals("Updated Name", updateCaptor.getValue().getName());
        assertEquals(ProductStatus.ACTIVE, updateCaptor.getValue().getStatus());

        ArgumentCaptor<Iterable<ImportJobRow>> rowsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(rowRepository).saveAll(rowsCaptor.capture());
        List<ImportJobRow> savedRows = toList(rowsCaptor.getValue());
        assertEquals(1, savedRows.size());
        assertEquals("bad-1", savedRows.get(0).getSku());
        assertEquals("name is required", savedRows.get(0).getErrorMessage());

        ArgumentCaptor<byte[]> csvCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(storageService).putBytes(eq(UploadFolder.IMPORT_ERROR_REPORT), eq(OWNER_ID), eq("text/csv"), csvCaptor.capture());
        String errorCsv = new String(csvCaptor.getValue(), StandardCharsets.UTF_8);
        assertTrue(errorCsv.contains("_error"));
        assertTrue(errorCsv.contains("bad-1"));
        assertTrue(errorCsv.contains("name is required"));

        verify(productService).addProductImage(eq(COMPANY_ID), eq(CREATE_PRODUCT_ID), eq(OWNER_ID), any(AddProductImageRequest.class));
    }

    @Test
    void processJob_inventorySyncBuildsBulkAdjustAndRecordsRowErrors() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.INVENTORY_SYNC, ImportMode.UPSERT, ImportJobStatus.PENDING);
        Product tracked = product("tracked-1", "Tracked", BigDecimal.TEN, 5);
        tracked.setId(UPDATE_PRODUCT_ID);
        Product untracked = product("untracked-1", "Untracked", BigDecimal.TEN, null);
        untracked.setId(TestIds.uuid(40));

        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv")).thenReturn(csvStream(inventorySyncCsv()));
        when(productRepository.findAllBySkuInAndCompanyId(anyCollection(), eq(COMPANY_ID)))
                .thenReturn(List.of(tracked, untracked));
        when(storageService.putBytes(eq(UploadFolder.IMPORT_ERROR_REPORT), eq(OWNER_ID), eq("text/csv"), any(byte[].class)))
                .thenReturn(new PresignUploadResponse("upload", "file", "reports/inventory-errors.csv", 600));

        service.processJob(JOB_ID);

        assertEquals(ImportJobStatus.COMPLETED_WITH_ERRORS, job.getStatus());
        assertEquals(3, job.getTotalRows());
        assertEquals(3, job.getProcessedRows());
        assertEquals(1, job.getSuccessRows());
        assertEquals(2, job.getErrorRows());

        ArgumentCaptor<BulkAdjustRequest> adjustCaptor = ArgumentCaptor.forClass(BulkAdjustRequest.class);
        verify(inventoryService).bulkAdjust(eq(COMPANY_ID), eq(OWNER_ID), adjustCaptor.capture());
        assertEquals(1, adjustCaptor.getValue().getItems().size());
        assertEquals(UPDATE_PRODUCT_ID, adjustCaptor.getValue().getItems().get(0).getProductId());
        assertEquals(2, adjustCaptor.getValue().getItems().get(0).getDelta());
        assertEquals(AdjustmentReason.IMPORT_SYNC, adjustCaptor.getValue().getItems().get(0).getReason());
        assertTrue(adjustCaptor.getValue().getItems().get(0).getNote().contains(JOB_ID.toString()));

        ArgumentCaptor<Iterable<ImportJobRow>> rowsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(rowRepository).saveAll(rowsCaptor.capture());
        List<ImportJobRow> savedRows = toList(rowsCaptor.getValue());
        assertEquals(2, savedRows.size());
        assertTrue(savedRows.stream().anyMatch(row -> row.getErrorMessage().contains("SKU not found")));
        assertTrue(savedRows.stream().anyMatch(row -> row.getErrorMessage().contains("Stock tracking is disabled")));
    }

    @Test
    void processJob_skipsWhenCompanyNoLongerExists() {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(false);

        service.processJob(JOB_ID);

        assertEquals(ImportJobStatus.FAILED, job.getStatus());
        assertTrue(job.getFailureReason().contains("Company no longer exists"));
        verify(storageService, never()).openObject(anyString());
    }

    @Test
    void processJob_throwsWhenCsvExceedsRowLimit() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        // Build a CSV with 50,001 data rows (exceeds IMPORT_MAX_ROWS = 50,000)
        StringBuilder sb = new StringBuilder("SKU,NAME,PRICE\n");
        for (int i = 0; i <= 50_000; i++) {
            sb.append("sku-").append(i).append(",Product ").append(i).append(",9.99\n");
        }
        when(storageService.openObject("imports/products.csv")).thenReturn(csvStream(sb.toString()));

        service.processJob(JOB_ID);

        assertEquals(ImportJobStatus.FAILED, job.getStatus());
        assertTrue(job.getFailureReason().contains("50000 rows"));
    }

    @Test
    void markJobFailed_updatesOnlyNonTerminalJobs() {
        ImportJob pending = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PROCESSING);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(pending));

        service.markJobFailed(JOB_ID, "worker crashed");

        assertEquals(ImportJobStatus.FAILED, pending.getStatus());
        assertEquals("worker crashed", pending.getFailureReason());

        ImportJob completed = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.COMPLETED);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(completed));

        service.markJobFailed(JOB_ID, "late failure");

        assertEquals(ImportJobStatus.COMPLETED, completed.getStatus());
        assertFalse("late failure".equals(completed.getFailureReason()));
    }

    private ImportJob job(UUID id, ImportJobType type, ImportMode mode, ImportJobStatus status) {
        ImportJob job = new ImportJob();
        job.setId(id);
        job.setCompanyId(COMPANY_ID);
        job.setUploadedBy(OWNER_ID);
        job.setJobType(type);
        job.setMode(mode);
        job.setStatus(status);
        job.setCsvS3Key("imports/products.csv");
        job.setFileName("products.csv");
        job.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        job.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        return job;
    }

    private Product product(String sku, String name, BigDecimal price, Integer stock) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setSku(sku);
        product.setName(name);
        product.setPrice(price);
        product.setStock(stock);
        product.setCurrency("USD");
        product.setStatus(ProductStatus.DRAFT);
        product.setImages(new ArrayList<>());
        return product;
    }

    private ProductResponse productResponse(UUID id, String sku) {
        return new ProductResponse(
                id,
                COMPANY_ID,
                "Product " + sku,
                null,
                sku,
                BigDecimal.TEN,
                null,
                "USD",
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                1,
                null,
                null,
                null,
                "DRAFT",
                null,
                null,
                false,
                true,
                true,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private AttachImagesRequest.Item item(String sku, String imageUrl, int displayOrder) {
        AttachImagesRequest.Item item = new AttachImagesRequest.Item();
        item.setSku(sku);
        item.setImageUrl(imageUrl);
        item.setDisplayOrder(displayOrder);
        return item;
    }

    private ByteArrayInputStream csvStream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }

    private String productUpsertCsv() {
        return String.join("\n",
                String.join(",",
                        "SKU", "NAME", "PRICE", "STATUS", "FEATURED", "IMAGEURL1"),
                "create-1,New Chair,11.50,ACTIVE,yes,https://cdn.test/create-1.jpg",
                "update-1,Updated Name,17.25,ACTIVE,no,",
                "bad-1,,4.99,DRAFT,yes,"
        );
    }

    private String inventorySyncCsv() {
        return String.join("\n",
                String.join(",", "sku", "stock"),
                "tracked-1,7",
                "missing-1,4",
                "untracked-1,3"
        );
    }

    private List<ImportJobRow> toList(Iterable<ImportJobRow> rows) {
        List<ImportJobRow> list = new ArrayList<>();
        for (ImportJobRow row : rows) {
            list.add(row);
        }
        return list;
    }

    // ─── listJobs ─────────────────────────────────────────────────────────────

    @Test
    void listJobs_returnsPagedJobResponses() {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.COMPLETED);
        when(jobRepository.findAllByCompanyIdOrderByCreatedAtDesc(eq(COMPANY_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job), PageRequest.of(0, 10), 1));

        PagedResponse<ImportJobResponse> response = service.listJobs(COMPANY_ID, OWNER_ID, 0, 10);

        verify(companyAccessService).requireAnyAccess(COMPANY_ID, OWNER_ID);
        assertEquals(1, response.getItems().size());
        assertEquals(JOB_ID, response.getItems().get(0).getId());
    }

    // ─── getErrorReport success path ──────────────────────────────────────────

    @Test
    void getErrorReport_withStoredKey_returnsDownloadUrl() {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.COMPLETED_WITH_ERRORS);
        job.setErrorReportS3Key("reports/job-errors.csv");
        when(jobRepository.findByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(Optional.of(job));
        when(storageService.presignDownload("reports/job-errors.csv", 600))
                .thenReturn("https://download.test/job-errors.csv");

        ImportDownloadResponse response = service.getErrorReport(COMPANY_ID, OWNER_ID, JOB_ID);

        assertEquals("https://download.test/job-errors.csv", response.getUrl());
        assertEquals(600, response.getExpiresIn());
    }

    // ─── createJob with PRODUCT_UPSERT type ───────────────────────────────────

    @Test
    void createJob_productUpsertRequiresManageProductsCapability() {
        CreateImportJobRequest request = new CreateImportJobRequest();
        request.setJobType(ImportJobType.PRODUCT_UPSERT);
        request.setCsvS3Key("imports/products.csv");
        request.setFileName("products.csv");
        request.setMode(ImportMode.UPDATE_ONLY);

        when(jobRepository.save(any(ImportJob.class))).thenAnswer(inv -> {
            ImportJob j = inv.getArgument(0);
            j.setId(JOB_ID);
            return j;
        });

        service.createJob(COMPANY_ID, OWNER_ID, request);

        verify(companyAccessService).require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_PRODUCTS);
    }

    // ─── getJob with PENDING status (progress = 0) ────────────────────────────

    @Test
    void getJob_pendingStatus_progressIsZero() {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        when(jobRepository.findByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(Optional.of(job));

        ImportJobResponse response = service.getJob(COMPANY_ID, OWNER_ID, JOB_ID);

        assertEquals(0, response.getProgressPercent());
        assertFalse(response.isHasErrorReport());
    }

    @Test
    void getJob_withPartialProgress_computesPercentage() {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PROCESSING);
        job.setTotalRows(100);
        job.setProcessedRows(50);
        when(jobRepository.findByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(Optional.of(job));

        ImportJobResponse response = service.getJob(COMPANY_ID, OWNER_ID, JOB_ID);

        assertEquals(50, response.getProgressPercent());
    }

    // ─── processJob — EXPORT job type (no-op) ─────────────────────────────────

    @Test
    void processJob_exportJobType_isNoOp() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.EXPORT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv")).thenReturn(csvStream("SKU,NAME,PRICE\n"));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        service.processJob(JOB_ID);

        verify(productService, never()).batchCreateProducts(any(), any(), any());
        verify(inventoryService, never()).bulkAdjust(any(), any(), any());
    }

    // ─── processJob — UPDATE_ONLY mode ────────────────────────────────────────

    @Test
    void processJob_updateOnlyMode_rejectsNewSkus() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPDATE_ONLY, ImportJobStatus.PENDING);
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE\nnew-sku,New Product,10.00\n"));
        when(productRepository.findAllBySkuInAndCompanyId(anyCollection(), eq(COMPANY_ID)))
                .thenReturn(List.of());
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.putBytes(any(), any(), any(), any()))
                .thenReturn(new PresignUploadResponse("upload", "file", "reports/errors.csv", 600));

        service.processJob(JOB_ID);

        assertEquals(1, job.getErrorRows());
        verify(productService, never()).batchCreateProducts(any(), any(), any());
    }

    // ─── processJob — CREATE_ONLY mode ────────────────────────────────────────

    @Test
    void processJob_createOnlyMode_rejectsExistingSkus() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.CREATE_ONLY, ImportJobStatus.PENDING);
        Product existing = product("existing-sku", "Existing", new BigDecimal("9.99"), 4);
        existing.setId(UPDATE_PRODUCT_ID);

        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE\nexisting-sku,Old Chair,15.00\n"));
        when(productRepository.findAllBySkuInAndCompanyId(anyCollection(), eq(COMPANY_ID)))
                .thenReturn(List.of(existing));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.putBytes(any(), any(), any(), any()))
                .thenReturn(new PresignUploadResponse("upload", "file", "reports/errors.csv", 600));

        service.processJob(JOB_ID);

        assertEquals(1, job.getErrorRows());
        verify(productService, never()).updateProduct(any(), any(), any(), any());
    }

    // ─── processJob — null job after successful claim ─────────────────────────

    @Test
    void processJob_nullJobAfterClaim_returnsGracefully() {
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        service.processJob(JOB_ID);

        verify(storageService, never()).openObject(anyString());
    }

    // ─── processJob — COMPLETED when no errors ────────────────────────────────

    @Test
    void processJob_noErrors_completesWithoutErrorReport() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        Product existing = product("good-sku", "Good Product", new BigDecimal("9.99"), 4);
        existing.setId(UPDATE_PRODUCT_ID);

        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE\ngood-sku,Good Product,9.99\n"));
        when(productRepository.findAllBySkuInAndCompanyId(anyCollection(), eq(COMPANY_ID)))
                .thenReturn(List.of(existing));
        when(productService.updateProduct(eq(COMPANY_ID), eq(UPDATE_PRODUCT_ID), eq(OWNER_ID), any(UpdateProductRequest.class)))
                .thenReturn(productResponse(UPDATE_PRODUCT_ID, "good-sku"));

        service.processJob(JOB_ID);

        assertEquals(0, job.getErrorRows());
        assertEquals(ImportJobStatus.COMPLETED, job.getStatus());
        verify(storageService, never()).putBytes(any(), any(), any(), any());
    }

    // ─── getJob — not found ───────────────────────────────────────────────────

    @Test
    void getJob_notFound_throwsResourceNotFoundException() {
        when(jobRepository.findByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getJob(COMPANY_ID, OWNER_ID, JOB_ID));
    }

    // ─── processJob — bumpProgress saves to repo to update counters ───────────

    @Test
    void processJob_successfulRun_bumpProgressUpdatesCounters() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        Product existing = product("counter-sku", "Counter Product", new BigDecimal("5.00"), 2);
        existing.setId(UPDATE_PRODUCT_ID);

        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE\ncounter-sku,Counter,5.00\n"));
        when(productRepository.findAllBySkuInAndCompanyId(anyCollection(), eq(COMPANY_ID)))
                .thenReturn(List.of(existing));
        when(productService.updateProduct(any(), any(), any(), any()))
                .thenReturn(productResponse(UPDATE_PRODUCT_ID, "counter-sku"));

        service.processJob(JOB_ID);

        assertEquals(ImportJobStatus.COMPLETED, job.getStatus());
        assertEquals(1, job.getSuccessRows());
        assertEquals(0, job.getErrorRows());
    }

    // ─── processJob — inventory sync delta=0 ─────────────────────────────────

    @Test
    void processJob_inventorySync_zeroDelta_skipsAdjustment() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.INVENTORY_SYNC, ImportMode.UPSERT, ImportJobStatus.PENDING);
        Product tracked = product("same-stock", "Same", BigDecimal.TEN, 5);
        tracked.setId(UPDATE_PRODUCT_ID);

        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("sku,stock\nsame-stock,5\n"));  // delta = 5 - 5 = 0
        when(productRepository.findAllBySkuInAndCompanyId(anyCollection(), eq(COMPANY_ID)))
                .thenReturn(List.of(tracked));

        service.processJob(JOB_ID);

        verify(inventoryService, never()).bulkAdjust(any(), any(), any());
    }

    // ─── validatePerJobType — validation edge cases ───────────────────────────

    @Test
    void processJob_negativeStockInCsv_marksRowAsError() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.INVENTORY_SYNC, ImportMode.UPSERT, ImportJobStatus.PENDING);
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("sku,stock\nbad-sku,-3\n"));

        service.processJob(JOB_ID);

        assertEquals(1, job.getErrorRows());
        verify(inventoryService, never()).bulkAdjust(any(), any(), any());
    }

    @Test
    void processJob_invalidStockNonInteger_marksRowAsError() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.INVENTORY_SYNC, ImportMode.UPSERT, ImportJobStatus.PENDING);
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("sku,stock\nbad-sku,abc\n"));

        service.processJob(JOB_ID);

        assertEquals(1, job.getErrorRows());
    }

    @Test
    void processJob_missingSku_marksRowAsError() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE\n,Missing SKU,9.99\n"));

        service.processJob(JOB_ID);

        assertEquals(1, job.getErrorRows());
    }

    @Test
    void processJob_negativePriceInProductUpsert_marksRowAsError() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE\nneg-price,Chair,-5.00\n"));
        when(productRepository.findAllBySkuInAndCompanyId(anyCollection(), eq(COMPANY_ID)))
                .thenReturn(List.of());

        service.processJob(JOB_ID);

        assertEquals(1, job.getErrorRows());
        verify(productService, never()).batchCreateProducts(any(), any(), any());
    }

    // ─── validatePerJobType — additional branches ─────────────────────────────

    @Test
    void processJob_missingPrice_marksRowAsError() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE\nmissing-price,Chair,\n"));

        service.processJob(JOB_ID);

        assertEquals(1, job.getErrorRows());
        verify(productService, never()).batchCreateProducts(any(), any(), any());
    }

    @Test
    void processJob_invalidPriceFormat_marksRowAsError() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE\nbad-price,Chair,not-a-number\n"));

        service.processJob(JOB_ID);

        assertEquals(1, job.getErrorRows());
    }

    @Test
    void processJob_invalidStatus_marksRowAsError() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE,STATUS\ninvalid-status,Chair,9.99,DELETED\n"));

        service.processJob(JOB_ID);

        assertEquals(1, job.getErrorRows());
    }

    @Test
    void processJob_stockInProductUpsert_negativeStock_marksRowAsError() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE,STOCK\nneg-stock,Chair,9.99,-1\n"));

        service.processJob(JOB_ID);

        assertEquals(1, job.getErrorRows());
    }

    @Test
    void processJob_stockInProductUpsert_nonIntegerStock_marksRowAsError() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE,STOCK\nbad-stock,Chair,9.99,abc\n"));

        service.processJob(JOB_ID);

        assertEquals(1, job.getErrorRows());
    }

    @Test
    void processJob_invalidBoolean_inFeatured_marksRowAsError() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE,FEATURED\nbool-test,Chair,9.99,maybe\n"));

        service.processJob(JOB_ID);

        assertEquals(1, job.getErrorRows());
    }

    // ─── bumpProgress — concurrency retry ─────────────────────────────────────

    @Test
    void processJob_bumpProgress_retryOnConcurrencyFailure_thenSucceeds() throws Exception {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPSERT, ImportJobStatus.PENDING);
        Product existing = product("retry-sku", "Retry Product", new BigDecimal("9.99"), 4);
        existing.setId(UPDATE_PRODUCT_ID);

        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE\nretry-sku,Retry Product,9.99\n"));
        when(productRepository.findAllBySkuInAndCompanyId(anyCollection(), eq(COMPANY_ID)))
                .thenReturn(List.of(existing));
        when(productService.updateProduct(any(), any(), any(), any()))
                .thenReturn(productResponse(UPDATE_PRODUCT_ID, "retry-sku"));

        // First three saves succeed (setTotalRows, markStatus PROCESSING), then on bumpProgress: fail once then succeed
        when(jobRepository.save(any(ImportJob.class)))
                .thenAnswer(inv -> inv.getArgument(0))  // setTotalRows save
                .thenAnswer(inv -> inv.getArgument(0))  // markStatus PROCESSING save
                .thenThrow(new ConcurrencyFailureException("conflict"))  // bumpProgress first attempt
                .thenAnswer(inv -> inv.getArgument(0))  // bumpProgress retry
                .thenAnswer(inv -> inv.getArgument(0)); // finalizeJob save

        assertDoesNotThrow(() -> service.processJob(JOB_ID));
    }

    // ─── batchCreate exception path ────────────────────────────────────────────

    @Test
    void processJob_batchCreateThrows_marksRowAsError() {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.CREATE_ONLY, ImportJobStatus.PENDING);

        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE\nnew-sku,New Product,19.99\n"));
        when(productRepository.findAllBySkuInAndCompanyId(anyCollection(), eq(COMPANY_ID)))
                .thenReturn(List.of()); // no existing → goes to creates
        when(productService.batchCreateProducts(any(), any(), any()))
                .thenThrow(new RuntimeException("batch create DB error"));
        when(storageService.putBytes(any(), any(), anyString(), any()))
                .thenReturn(new PresignUploadResponse("https://s3/presign-url", "https://s3/errors.csv", "reports/key.csv", 3600));
        when(storageService.presignDownload(anyString(), anyInt()))
                .thenReturn("https://s3/errors.csv");

        service.processJob(JOB_ID);

        // Error report should be written since batchCreate failed → row has error
        verify(storageService).putBytes(any(), any(), anyString(), any());
    }

    // ─── updateProduct exception path ─────────────────────────────────────────

    @Test
    void processJob_updateProductThrows_marksRowAsError() {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPDATE_ONLY, ImportJobStatus.PENDING);
        Product existing = product("upd-sku", "Update Product", new BigDecimal("10.00"), 5);
        existing.setId(UPDATE_PRODUCT_ID);

        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE\nupd-sku,Updated,25.00\n"));
        when(productRepository.findAllBySkuInAndCompanyId(anyCollection(), eq(COMPANY_ID)))
                .thenReturn(List.of(existing));
        when(productService.updateProduct(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("update DB error"));
        when(storageService.putBytes(any(), any(), anyString(), any()))
                .thenReturn(new PresignUploadResponse("https://s3/presign-url", "https://s3/errors.csv", "reports/key.csv", 3600));
        when(storageService.presignDownload(anyString(), anyInt()))
                .thenReturn("https://s3/errors.csv");

        service.processJob(JOB_ID);

        verify(storageService).putBytes(any(), any(), anyString(), any());
    }

    // ─── bulkAdjust exception path ────────────────────────────────────────────

    @Test
    void processJob_bulkAdjustThrows_marksRowAsError() {
        ImportJob job = job(JOB_ID, ImportJobType.INVENTORY_SYNC, ImportMode.UPSERT, ImportJobStatus.PENDING);
        Product existing = product("adj-sku", "Adj Product", new BigDecimal("10.00"), 20);
        existing.setId(UPDATE_PRODUCT_ID);

        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,STOCK\nadj-sku,25\n")); // delta +5
        when(productRepository.findAllBySkuInAndCompanyId(anyCollection(), eq(COMPANY_ID)))
                .thenReturn(List.of(existing));
        when(inventoryService.bulkAdjust(any(), any(), any()))
                .thenThrow(new RuntimeException("bulkAdjust DB error"));
        when(storageService.putBytes(any(), any(), anyString(), any()))
                .thenReturn(new PresignUploadResponse("https://s3/presign-url", "https://s3/errors.csv", "reports/key.csv", 3600));
        when(storageService.presignDownload(anyString(), anyInt()))
                .thenReturn("https://s3/errors.csv");

        service.processJob(JOB_ID);

        verify(storageService).putBytes(any(), any(), anyString(), any());
    }

    // ─── bumpProgress — exhausts all 5 retries ────────────────────────────────

    @Test
    void processJob_bumpProgress_exhaustsRetries_rethrows() {
        ImportJob job = job(JOB_ID, ImportJobType.PRODUCT_UPSERT, ImportMode.UPDATE_ONLY, ImportJobStatus.PENDING);
        Product existing = product("exh-sku", "Exhaust Product", new BigDecimal("9.99"), 5);
        existing.setId(UPDATE_PRODUCT_ID);

        when(jobRepository.claimForProcessing(JOB_ID, ImportJobStatus.PENDING, ImportJobStatus.PARSING)).thenReturn(1);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(storageService.openObject("imports/products.csv"))
                .thenReturn(csvStream("SKU,NAME,PRICE\nexh-sku,Exhaust,9.99\n"));
        when(productRepository.findAllBySkuInAndCompanyId(anyCollection(), eq(COMPANY_ID)))
                .thenReturn(List.of(existing));
        when(productService.updateProduct(any(), any(), any(), any()))
                .thenReturn(productResponse(UPDATE_PRODUCT_ID, "exh-sku"));

        // calls 1-2: setTotalRows and markStatus PROCESSING succeed; calls 3-7: bumpProgress retries 1-5 all fail
        when(jobRepository.save(any(ImportJob.class)))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenThrow(new ConcurrencyFailureException("c1"))
                .thenThrow(new ConcurrencyFailureException("c2"))
                .thenThrow(new ConcurrencyFailureException("c3"))
                .thenThrow(new ConcurrencyFailureException("c4"))
                .thenThrow(new ConcurrencyFailureException("c5"));

        assertThrows(ConcurrencyFailureException.class, () -> service.processJob(JOB_ID));
    }

    // ─── exportCatalogue — multi-page ──────────────────────────────────────────

    @Test
    void exportCatalogue_multiPage_iteratesBothPages() {
        Product p1 = product("sku-1", "Product 1", new BigDecimal("10.00"), 5);
        p1.setStatus(ProductStatus.ACTIVE);
        p1.setImages(new ArrayList<>());
        Product p2 = product("sku-2", "Product 2", new BigDecimal("20.00"), 3);
        p2.setStatus(ProductStatus.ACTIVE);
        p2.setImages(new ArrayList<>());

        when(productRepository.countByCompanyId(COMPANY_ID)).thenReturn(2L);

        // totalElements=2000 with pageSize=1000 → totalPages=2 → page0.hasNext()=true, page1.hasNext()=false
        var page0 = new PageImpl<>(List.of(p1), PageRequest.of(0, 1000), 2000);
        var page1 = new PageImpl<>(List.of(p2), PageRequest.of(1, 1000), 2000);
        when(productRepository.findAllByCompanyId(eq(COMPANY_ID), any(Pageable.class)))
                .thenReturn(page0, page1);

        when(storageService.putBytes(any(), any(), anyString(), any()))
                .thenReturn(new PresignUploadResponse("https://s3/presign-url", "https://s3/out.csv", "exports/out.csv", 3600));
        when(storageService.presignDownload(anyString(), anyInt()))
                .thenReturn("https://s3/out.csv");

        ImportDownloadResponse response = service.exportCatalogue(COMPANY_ID, OWNER_ID);

        assertNotNull(response);
        verify(productRepository, times(2)).findAllByCompanyId(eq(COMPANY_ID), any(Pageable.class));
    }
}
