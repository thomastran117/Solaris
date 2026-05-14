package backend.services.impl.imports;

import backend.dtos.requests.imports.AttachImagesRequest;
import backend.dtos.requests.imports.CreateImportJobRequest;
import backend.dtos.requests.inventory.BulkAdjustItem;
import backend.dtos.requests.inventory.BulkAdjustRequest;
import backend.dtos.requests.product.AddProductImageRequest;
import backend.dtos.requests.product.BatchCreateProductsRequest;
import backend.dtos.requests.product.CreateProductRequest;
import backend.dtos.requests.product.UpdateProductRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.imports.ImportDownloadResponse;
import backend.dtos.responses.imports.ImportJobResponse;
import backend.dtos.responses.imports.ImportJobRowResponse;
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
import backend.repositories.ImportJobRepository;
import backend.repositories.ImportJobRowRepository;
import backend.repositories.ProductRepository;
import backend.services.intf.SanitizationService;
import backend.services.intf.StorageService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.imports.ImportService;
import backend.services.intf.inventory.InventoryService;
import backend.services.intf.products.ProductService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImportServiceImpl implements ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportServiceImpl.class);

    private static final int BATCH_SIZE = 50;
    private static final int DOWNLOAD_EXPIRY_SECONDS = 600;
    private static final int EXPORT_MAX_PRODUCTS = 50_000;

    private final ImportJobRepository jobRepository;
    private final ImportJobRowRepository rowRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final CompanyAccessService companyAccessService;
    private final SanitizationService sanitizationService;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    public ImportServiceImpl(
            ImportJobRepository jobRepository,
            ImportJobRowRepository rowRepository,
            ProductRepository productRepository,
            ProductService productService,
            InventoryService inventoryService,
            CompanyAccessService companyAccessService,
            SanitizationService sanitizationService,
            StorageService storageService,
            ApplicationEventPublisher eventPublisher) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.companyAccessService = companyAccessService;
        this.sanitizationService = sanitizationService;
        this.storageService = storageService;
        this.eventPublisher = eventPublisher;
    }

    // -------------------------------------------------------------------------
    // Public API — invoked by the controller
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public ImportJobResponse createJob(long companyId, long ownerId, CreateImportJobRequest request) {
        CompanyCapability cap = request.getJobType() == ImportJobType.INVENTORY_SYNC
                ? CompanyCapability.MANAGE_INVENTORY
                : CompanyCapability.MANAGE_PRODUCTS;
        companyAccessService.require(companyId, ownerId, cap);

        ImportJob job = new ImportJob();
        job.setCompanyId(companyId);
        job.setUploadedBy(ownerId);
        job.setJobType(request.getJobType());
        job.setMode(request.getMode() != null ? request.getMode() : ImportMode.UPSERT);
        job.setStatus(ImportJobStatus.PENDING);
        job.setCsvS3Key(request.getCsvS3Key());
        job.setFileName(request.getFileName());
        ImportJob saved = jobRepository.save(job);

        eventPublisher.publishEvent(new ImportJobRequestedEvent(saved.getId(), companyId, ownerId));
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ImportJobResponse getJob(long companyId, long ownerId, long jobId) {
        companyAccessService.requireAnyAccess(companyId, ownerId);
        return toResponse(loadJob(companyId, jobId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ImportJobResponse> listJobs(long companyId, long ownerId, int page, int size) {
        companyAccessService.requireAnyAccess(companyId, ownerId);
        Page<ImportJob> jobs = jobRepository.findAllByCompanyIdOrderByCreatedAtDesc(
                companyId, PageRequest.of(page, size));
        return new PagedResponse<>(jobs.map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ImportJobRowResponse> listErrors(long companyId, long ownerId, long jobId, int page, int size) {
        companyAccessService.requireAnyAccess(companyId, ownerId);
        ImportJob job = loadJob(companyId, jobId);
        Page<ImportJobRow> rows = rowRepository.findAllByJobIdOrderByRowNumberAsc(
                job.getId(), PageRequest.of(page, size));
        return new PagedResponse<>(rows.map(this::toRowResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ImportDownloadResponse getErrorReport(long companyId, long ownerId, long jobId) {
        companyAccessService.requireAnyAccess(companyId, ownerId);
        ImportJob job = loadJob(companyId, jobId);
        if (job.getErrorReportS3Key() == null) {
            throw new ResourceNotFoundException("No error report available for this job");
        }
        String url = storageService.presignDownload(job.getErrorReportS3Key(), DOWNLOAD_EXPIRY_SECONDS);
        return new ImportDownloadResponse(url, DOWNLOAD_EXPIRY_SECONDS);
    }

    @Override
    @Transactional
    public ImportDownloadResponse exportCatalogue(long companyId, long ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        List<Product> products = productRepository.findAllByCompanyId(companyId);
        if (products.size() > EXPORT_MAX_PRODUCTS) {
            throw new BadRequestException(
                    "Catalogue export exceeds " + EXPORT_MAX_PRODUCTS + " rows. Contact support for bulk export.");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer,
                     CSVFormat.DEFAULT.builder()
                             .setHeader(ImportCsvSchema.EXPORT_COLUMNS.toArray(new String[0]))
                             .build())) {
            for (Product p : products) {
                printer.printRecord(toExportRow(p));
            }
            printer.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write export CSV", e);
        }

        PresignUploadResponse stored = storageService.putBytes(
                UploadFolder.IMPORT_ERROR_REPORT, ownerId, "text/csv", out.toByteArray());
        String url = storageService.presignDownload(stored.getKey(), DOWNLOAD_EXPIRY_SECONDS);
        return new ImportDownloadResponse(url, DOWNLOAD_EXPIRY_SECONDS);
    }

    @Override
    @Transactional
    public int attachImages(long companyId, long ownerId, AttachImagesRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Map<String, List<AttachImagesRequest.Item>> bySku = new HashMap<>();
        for (AttachImagesRequest.Item item : request.getItems()) {
            bySku.computeIfAbsent(item.getSku().trim().toLowerCase(), k -> new ArrayList<>()).add(item);
        }

        List<Product> products = productRepository.findAllBySkuInAndCompanyId(bySku.keySet(), companyId);
        Map<String, Product> productBySku = new HashMap<>();
        for (Product p : products) {
            if (p.getSku() != null) {
                productBySku.put(p.getSku().toLowerCase(), p);
            }
        }

        int attached = 0;
        for (Map.Entry<String, List<AttachImagesRequest.Item>> entry : bySku.entrySet()) {
            Product product = productBySku.get(entry.getKey());
            if (product == null) continue;
            List<AttachImagesRequest.Item> items = entry.getValue();
            items.sort((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()));
            for (AttachImagesRequest.Item item : items) {
                try {
                    AddProductImageRequest add = new AddProductImageRequest();
                    add.setImageUrl(item.getImageUrl());
                    productService.addProductImage(companyId, product.getId(), ownerId, add);
                    attached++;
                } catch (Exception ex) {
                    log.warn("[IMPORT] Failed to attach image sku={} url={} reason={}",
                            item.getSku(), item.getImageUrl(), ex.getMessage());
                }
            }
        }
        return attached;
    }

    // -------------------------------------------------------------------------
    // Worker — invoked by ImportJobConsumer
    // -------------------------------------------------------------------------

    @Override
    public void processJob(long jobId) {
        ImportJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("[IMPORT] worker received unknown jobId={}", jobId);
            return;
        }
        if (job.getStatus().isTerminal()) {
            log.info("[IMPORT] jobId={} already terminal ({}), skipping", jobId, job.getStatus());
            return;
        }

        markStatus(job, ImportJobStatus.PARSING, null);
        List<ParsedRow> rows;
        try {
            rows = parseAndValidate(job);
        } catch (Exception ex) {
            log.error("[IMPORT] parse failed for jobId={}", jobId, ex);
            markStatus(job, ImportJobStatus.FAILED, "Parse failed: " + ex.getMessage());
            return;
        }
        job.setTotalRows(rows.size());
        jobRepository.save(job);

        markStatus(job, ImportJobStatus.PROCESSING, null);
        switch (job.getJobType()) {
            case PRODUCT_UPSERT -> runProductUpsert(job, rows);
            case INVENTORY_SYNC -> runInventorySync(job, rows);
            case EXPORT -> { /* exports are sync via exportCatalogue, no worker work */ }
        }

        // Persist failed rows to an error CSV
        if (job.getErrorRows() > 0) {
            try {
                String key = writeErrorReport(job, rows);
                job.setErrorReportS3Key(key);
            } catch (Exception ex) {
                log.warn("[IMPORT] failed to write error report for jobId={}", jobId, ex);
            }
        }

        ImportJobStatus terminal = job.getErrorRows() > 0
                ? ImportJobStatus.COMPLETED_WITH_ERRORS
                : ImportJobStatus.COMPLETED;
        markStatus(job, terminal, null);
    }

    // -------------------------------------------------------------------------
    // CSV parsing
    // -------------------------------------------------------------------------

    /**
     * Holds one parsed row plus its validation outcome. Successful rows reach the
     * worker; failed rows are logged to {@link ImportJobRow} and re-emitted in the
     * error CSV.
     */
    static final class ParsedRow {
        int rowNumber;
        Map<String, String> values = new HashMap<>();
        String sku;
        String error; // null when valid
        boolean processedOk;
        boolean accountedAsError;

        boolean isValid() {
            return error == null;
        }
    }

    private List<ParsedRow> parseAndValidate(ImportJob job) throws IOException {
        List<ParsedRow> out = new ArrayList<>();
        try (InputStream stream = storageService.openObject(job.getCsvS3Key());
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            Map<String, String> headerLookup = caseInsensitiveHeaderMap(parser);

            int rowNumber = 0;
            for (CSVRecord rec : parser) {
                rowNumber++;
                ParsedRow row = new ParsedRow();
                row.rowNumber = rowNumber;

                for (Map.Entry<String, String> entry : headerLookup.entrySet()) {
                    String canonical = entry.getKey();
                    String actual = entry.getValue();
                    row.values.put(canonical, rec.isMapped(actual) ? rec.get(actual) : null);
                }

                row.sku = blankToNull(row.values.get(ImportCsvSchema.SKU));
                if (row.sku == null) {
                    row.error = "SKU is required";
                    out.add(row);
                    continue;
                }

                try {
                    validatePerJobType(row, job);
                } catch (RuntimeException ex) {
                    row.error = ex.getMessage();
                }
                out.add(row);
            }
        }
        return out;
    }

    private Map<String, String> caseInsensitiveHeaderMap(CSVParser parser) {
        Map<String, String> canonicalToActual = new HashMap<>();
        for (String actual : parser.getHeaderMap().keySet()) {
            if (actual == null) continue;
            String norm = actual.trim().toLowerCase();
            for (String canonical : ImportCsvSchema.EXPORT_COLUMNS) {
                if (canonical.toLowerCase().equals(norm)) {
                    canonicalToActual.put(canonical, actual);
                    break;
                }
            }
        }
        return canonicalToActual;
    }

    private void validatePerJobType(ParsedRow row, ImportJob job) {
        if (job.getJobType() == ImportJobType.INVENTORY_SYNC) {
            // Only stock is required for inventory sync; everything else ignored
            String stockStr = blankToNull(row.values.get(ImportCsvSchema.STOCK));
            if (stockStr == null) {
                throw new IllegalArgumentException("stock is required for inventory sync");
            }
            try {
                int stock = Integer.parseInt(stockStr);
                if (stock < 0) throw new IllegalArgumentException("stock must be >= 0");
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("stock is not a valid integer: " + stockStr);
            }
            return;
        }

        // PRODUCT_UPSERT — validate the fields that will populate CreateProductRequest
        String name = blankToNull(row.values.get(ImportCsvSchema.NAME));
        String priceStr = blankToNull(row.values.get(ImportCsvSchema.PRICE));
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        if (priceStr == null) {
            throw new IllegalArgumentException("price is required");
        }
        try {
            BigDecimal price = new BigDecimal(priceStr);
            if (price.signum() < 0) throw new IllegalArgumentException("price must be >= 0");
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("price is not a valid decimal: " + priceStr);
        }

        String stockStr = blankToNull(row.values.get(ImportCsvSchema.STOCK));
        if (stockStr != null) {
            try {
                int stock = Integer.parseInt(stockStr);
                if (stock < 0) throw new IllegalArgumentException("stock must be >= 0");
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("stock is not a valid integer: " + stockStr);
            }
        }

        String statusStr = blankToNull(row.values.get(ImportCsvSchema.STATUS));
        if (statusStr != null) {
            try {
                ProductStatus.valueOf(statusStr.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("status must be one of DRAFT/SCHEDULED/ACTIVE/ARCHIVED");
            }
        }

        for (String col : List.of(ImportCsvSchema.FEATURED, ImportCsvSchema.PURCHASABLE, ImportCsvSchema.LISTED)) {
            String raw = blankToNull(row.values.get(col));
            if (raw != null) {
                try { CsvBooleans.parse(raw); }
                catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(col + ": " + ex.getMessage());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Worker — PRODUCT_UPSERT
    // -------------------------------------------------------------------------

    private void runProductUpsert(ImportJob job, List<ParsedRow> rows) {
        // Look up existing SKUs in chunks
        List<String> skus = new ArrayList<>();
        for (ParsedRow r : rows) {
            if (r.isValid() && r.sku != null) skus.add(r.sku);
        }

        Map<String, Product> existing = new HashMap<>();
        for (int i = 0; i < skus.size(); i += 200) {
            List<String> chunk = skus.subList(i, Math.min(i + 200, skus.size()));
            for (Product p : productRepository.findAllBySkuInAndCompanyId(chunk, job.getCompanyId())) {
                if (p.getSku() != null) existing.put(p.getSku().toLowerCase(), p);
            }
        }

        List<ParsedRow> creates = new ArrayList<>();
        List<ParsedRow> updates = new ArrayList<>();
        for (ParsedRow row : rows) {
            if (!row.isValid()) continue;
            boolean exists = existing.containsKey(row.sku.toLowerCase());
            switch (job.getMode()) {
                case UPDATE_ONLY -> {
                    if (!exists) {
                        row.error = "Mode is UPDATE_ONLY but SKU does not exist";
                    } else {
                        updates.add(row);
                    }
                }
                case CREATE_ONLY -> {
                    if (exists) {
                        row.error = "Mode is CREATE_ONLY but SKU already exists";
                    } else {
                        creates.add(row);
                    }
                }
                case UPSERT -> {
                    if (exists) updates.add(row); else creates.add(row);
                }
            }
        }

        // Chunk creates into batchCreate calls
        for (int i = 0; i < creates.size(); i += BATCH_SIZE) {
            List<ParsedRow> chunk = creates.subList(i, Math.min(i + BATCH_SIZE, creates.size()));
            BatchCreateProductsRequest batch = new BatchCreateProductsRequest();
            List<CreateProductRequest> dtos = new ArrayList<>();
            for (ParsedRow row : chunk) dtos.add(toCreateRequest(row));
            batch.setProducts(dtos);
            try {
                sanitizationService.normalize(batch);
                var responses = productService.batchCreateProducts(
                        job.getCompanyId(), job.getUploadedBy(), batch);
                // Attach images for each created product (positions 1..5)
                for (int idx = 0; idx < chunk.size() && idx < responses.size(); idx++) {
                    long productId = responses.get(idx).getId();
                    attachRowImages(job, chunk.get(idx), productId);
                    chunk.get(idx).processedOk = true;
                }
            } catch (Exception ex) {
                for (ParsedRow row : chunk) {
                    row.error = "Batch create failed: " + ex.getMessage();
                }
            }
            bumpProgress(job, chunk);
        }

        // Updates: one-by-one (each has its own @Transactional + @Version inside ProductService)
        for (int i = 0; i < updates.size(); i++) {
            ParsedRow row = updates.get(i);
            Product target = existing.get(row.sku.toLowerCase());
            try {
                UpdateProductRequest req = toUpdateRequest(row);
                sanitizationService.normalize(req);
                productService.updateProduct(job.getCompanyId(), target.getId(), job.getUploadedBy(), req);
                attachRowImages(job, row, target.getId());
                row.processedOk = true;
            } catch (Exception ex) {
                row.error = "Update failed: " + ex.getMessage();
            }
            if ((i + 1) % BATCH_SIZE == 0 || i == updates.size() - 1) {
                int start = Math.max(0, i - (i % BATCH_SIZE));
                bumpProgress(job, updates.subList(start, i + 1));
            }
        }

        // Account for rows that were invalid from parse stage (never made it into batches)
        recordInvalidRowsAsErrors(job, rows);
    }

    private void attachRowImages(ImportJob job, ParsedRow row, long productId) {
        for (String col : ImportCsvSchema.IMAGE_COLUMNS) {
            String url = blankToNull(row.values.get(col));
            if (url == null) continue;
            try {
                AddProductImageRequest add = new AddProductImageRequest();
                add.setImageUrl(url);
                productService.addProductImage(job.getCompanyId(), productId, job.getUploadedBy(), add);
            } catch (Exception ex) {
                log.warn("[IMPORT] image attach failed jobId={} sku={} url={}: {}",
                        job.getId(), row.sku, url, ex.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Worker — INVENTORY_SYNC
    // -------------------------------------------------------------------------

    private void runInventorySync(ImportJob job, List<ParsedRow> rows) {
        // Resolve SKUs to product ids + current stock
        List<String> skus = new ArrayList<>();
        for (ParsedRow r : rows) if (r.isValid()) skus.add(r.sku);

        Map<String, Product> bySku = new HashMap<>();
        for (int i = 0; i < skus.size(); i += 200) {
            List<String> chunk = skus.subList(i, Math.min(i + 200, skus.size()));
            for (Product p : productRepository.findAllBySkuInAndCompanyId(chunk, job.getCompanyId())) {
                if (p.getSku() != null) bySku.put(p.getSku().toLowerCase(), p);
            }
        }

        // Build per-chunk BulkAdjustRequest of (productId, delta = csvStock - current)
        List<ParsedRow> readyRows = new ArrayList<>();
        List<BulkAdjustItem> readyItems = new ArrayList<>();
        for (ParsedRow row : rows) {
            if (!row.isValid()) continue;
            Product product = bySku.get(row.sku.toLowerCase());
            if (product == null) {
                row.error = "SKU not found in this company";
                continue;
            }
            if (product.getStock() == null) {
                row.error = "Stock tracking is disabled for this SKU (stock is null)";
                continue;
            }
            int csvStock = Integer.parseInt(row.values.get(ImportCsvSchema.STOCK).trim());
            int delta = csvStock - product.getStock();
            if (delta == 0) {
                row.processedOk = true;
                continue;
            }
            BulkAdjustItem item = new BulkAdjustItem();
            item.setProductId(product.getId());
            item.setDelta(delta);
            item.setReason(AdjustmentReason.IMPORT_SYNC);
            item.setNote("Import job " + job.getId());
            readyItems.add(item);
            readyRows.add(row);
        }

        for (int i = 0; i < readyItems.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, readyItems.size());
            List<BulkAdjustItem> chunk = readyItems.subList(i, end);
            List<ParsedRow> rowChunk = readyRows.subList(i, end);
            BulkAdjustRequest req = new BulkAdjustRequest();
            req.setItems(new ArrayList<>(chunk));
            try {
                inventoryService.bulkAdjust(job.getCompanyId(), job.getUploadedBy(), req);
                for (ParsedRow r : rowChunk) r.processedOk = true;
            } catch (Exception ex) {
                for (ParsedRow r : rowChunk) {
                    r.error = "Bulk adjust failed: " + ex.getMessage();
                }
            }
            bumpProgress(job, rowChunk);
        }

        recordInvalidRowsAsErrors(job, rows);
    }

    // -------------------------------------------------------------------------
    // Progress + error logging
    // -------------------------------------------------------------------------

    private void bumpProgress(ImportJob job, List<ParsedRow> chunk) {
        int success = 0, errors = 0;
        List<ImportJobRow> errorRows = new ArrayList<>();
        for (ParsedRow row : chunk) {
            if (row.processedOk) {
                success++;
            } else if (row.error != null) {
                errors++;
                errorRows.add(buildErrorRow(job.getId(), row));
            }
        }
        if (!errorRows.isEmpty()) rowRepository.saveAll(errorRows);
        job.setProcessedRows(job.getProcessedRows() + chunk.size());
        job.setSuccessRows(job.getSuccessRows() + success);
        job.setErrorRows(job.getErrorRows() + errors);
        jobRepository.save(job);
    }

    private void recordInvalidRowsAsErrors(ImportJob job, List<ParsedRow> allRows) {
        // Rows whose error never went through bumpProgress (invalid at parse, or
        // rejected before being added to a chunk) — log them now.
        List<ImportJobRow> errorRows = new ArrayList<>();
        for (ParsedRow row : allRows) {
            if (row.error != null && !row.accountedAsError) {
                errorRows.add(buildErrorRow(job.getId(), row));
            }
        }
        if (errorRows.isEmpty()) return;
        rowRepository.saveAll(errorRows);
        job.setProcessedRows(job.getProcessedRows() + errorRows.size());
        job.setErrorRows(job.getErrorRows() + errorRows.size());
        jobRepository.save(job);
    }

    private ImportJobRow buildErrorRow(long jobId, ParsedRow row) {
        row.accountedAsError = true;
        ImportJobRow entity = new ImportJobRow();
        entity.setJobId(jobId);
        entity.setRowNumber(row.rowNumber);
        entity.setSku(row.sku);
        String msg = row.error == null ? "Unknown error" : row.error;
        if (msg.length() > 1000) msg = msg.substring(0, 1000);
        entity.setErrorMessage(msg);
        return entity;
    }

    private void markStatus(ImportJob job, ImportJobStatus status, String failureReason) {
        job.setStatus(status);
        if (failureReason != null) {
            String trimmed = failureReason.length() > 1000 ? failureReason.substring(0, 1000) : failureReason;
            job.setFailureReason(trimmed);
        }
        jobRepository.save(job);
    }

    // -------------------------------------------------------------------------
    // Error report CSV writer
    // -------------------------------------------------------------------------

    private String writeErrorReport(ImportJob job, List<ParsedRow> rows) throws IOException {
        List<String> headers = new ArrayList<>(ImportCsvSchema.EXPORT_COLUMNS);
        headers.add("_error");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer,
                     CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build())) {
            for (ParsedRow row : rows) {
                if (row.error == null) continue;
                List<String> values = new ArrayList<>();
                for (String col : ImportCsvSchema.EXPORT_COLUMNS) {
                    String v = row.values.get(col);
                    values.add(v == null ? "" : v);
                }
                values.add(row.error);
                printer.printRecord(values);
            }
            printer.flush();
        }
        PresignUploadResponse stored = storageService.putBytes(
                UploadFolder.IMPORT_ERROR_REPORT, job.getUploadedBy(), "text/csv", out.toByteArray());
        return stored.getKey();
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private CreateProductRequest toCreateRequest(ParsedRow row) {
        CreateProductRequest req = new CreateProductRequest();
        req.setSku(row.sku);
        req.setName(row.values.get(ImportCsvSchema.NAME));
        req.setDescription(blankToNull(row.values.get(ImportCsvSchema.DESCRIPTION)));
        req.setPrice(new BigDecimal(row.values.get(ImportCsvSchema.PRICE).trim()));
        String compareAt = blankToNull(row.values.get(ImportCsvSchema.COMPARE_AT_PRICE));
        if (compareAt != null) req.setCompareAtPrice(new BigDecimal(compareAt.trim()));
        req.setCurrency(blankToNull(row.values.get(ImportCsvSchema.CURRENCY)));
        req.setCategory(blankToNull(row.values.get(ImportCsvSchema.CATEGORY)));
        req.setBrand(blankToNull(row.values.get(ImportCsvSchema.BRAND)));
        req.setTags(blankToNull(row.values.get(ImportCsvSchema.TAGS)));
        req.setThumbnailUrl(blankToNull(row.values.get(ImportCsvSchema.THUMBNAIL_URL)));
        String stock = blankToNull(row.values.get(ImportCsvSchema.STOCK));
        if (stock != null) req.setStock(Integer.parseInt(stock.trim()));
        String weight = blankToNull(row.values.get(ImportCsvSchema.WEIGHT));
        if (weight != null) req.setWeight(new BigDecimal(weight.trim()));
        req.setWeightUnit(blankToNull(row.values.get(ImportCsvSchema.WEIGHT_UNIT)));
        Boolean featured = CsvBooleans.parse(row.values.get(ImportCsvSchema.FEATURED));
        if (featured != null) req.setFeatured(featured);
        Boolean purchasable = CsvBooleans.parse(row.values.get(ImportCsvSchema.PURCHASABLE));
        if (purchasable != null) req.setPurchasable(purchasable);
        Boolean listed = CsvBooleans.parse(row.values.get(ImportCsvSchema.LISTED));
        if (listed != null) req.setListed(listed);
        String status = blankToNull(row.values.get(ImportCsvSchema.STATUS));
        if (status != null) req.setStatus(ProductStatus.valueOf(status.trim().toUpperCase()));
        return req;
    }

    private UpdateProductRequest toUpdateRequest(ParsedRow row) {
        UpdateProductRequest req = new UpdateProductRequest();
        req.setSku(row.sku);
        String name = blankToNull(row.values.get(ImportCsvSchema.NAME));
        if (name != null) req.setName(name);
        String description = blankToNull(row.values.get(ImportCsvSchema.DESCRIPTION));
        if (description != null) req.setDescription(description);
        String price = blankToNull(row.values.get(ImportCsvSchema.PRICE));
        if (price != null) req.setPrice(new BigDecimal(price.trim()));
        String compareAt = blankToNull(row.values.get(ImportCsvSchema.COMPARE_AT_PRICE));
        if (compareAt != null) req.setCompareAtPrice(new BigDecimal(compareAt.trim()));
        String currency = blankToNull(row.values.get(ImportCsvSchema.CURRENCY));
        if (currency != null) req.setCurrency(currency);
        String category = blankToNull(row.values.get(ImportCsvSchema.CATEGORY));
        if (category != null) req.setCategory(category);
        String brand = blankToNull(row.values.get(ImportCsvSchema.BRAND));
        if (brand != null) req.setBrand(brand);
        String tags = blankToNull(row.values.get(ImportCsvSchema.TAGS));
        if (tags != null) req.setTags(tags);
        String thumb = blankToNull(row.values.get(ImportCsvSchema.THUMBNAIL_URL));
        if (thumb != null) req.setThumbnailUrl(thumb);
        String stock = blankToNull(row.values.get(ImportCsvSchema.STOCK));
        if (stock != null) req.setStock(Integer.parseInt(stock.trim()));
        String weight = blankToNull(row.values.get(ImportCsvSchema.WEIGHT));
        if (weight != null) req.setWeight(new BigDecimal(weight.trim()));
        String weightUnit = blankToNull(row.values.get(ImportCsvSchema.WEIGHT_UNIT));
        if (weightUnit != null) req.setWeightUnit(weightUnit);
        Boolean featured = CsvBooleans.parse(row.values.get(ImportCsvSchema.FEATURED));
        if (featured != null) req.setFeatured(featured);
        Boolean purchasable = CsvBooleans.parse(row.values.get(ImportCsvSchema.PURCHASABLE));
        if (purchasable != null) req.setPurchasable(purchasable);
        Boolean listed = CsvBooleans.parse(row.values.get(ImportCsvSchema.LISTED));
        if (listed != null) req.setListed(listed);
        String status = blankToNull(row.values.get(ImportCsvSchema.STATUS));
        if (status != null) req.setStatus(ProductStatus.valueOf(status.trim().toUpperCase()));
        return req;
    }

    private List<String> toExportRow(Product p) {
        List<String> images = new ArrayList<>();
        if (p.getImages() != null) {
            for (ProductImage img : p.getImages()) images.add(img.getImageUrl());
        }
        return Arrays.asList(
                nullToEmpty(p.getSku()),
                nullToEmpty(p.getName()),
                nullToEmpty(p.getDescription()),
                p.getPrice() == null ? "" : p.getPrice().toPlainString(),
                p.getCompareAtPrice() == null ? "" : p.getCompareAtPrice().toPlainString(),
                nullToEmpty(p.getCurrency()),
                nullToEmpty(p.getCategory()),
                nullToEmpty(p.getBrand()),
                nullToEmpty(p.getTags()),
                p.getStock() == null ? "" : p.getStock().toString(),
                p.getWeight() == null ? "" : p.getWeight().toPlainString(),
                nullToEmpty(p.getWeightUnit()),
                Boolean.toString(p.isFeatured()),
                Boolean.toString(p.isPurchasable()),
                Boolean.toString(p.isListed()),
                p.getStatus() == null ? "" : p.getStatus().name(),
                nullToEmpty(p.getThumbnailUrl()),
                images.size() > 0 ? images.get(0) : "",
                images.size() > 1 ? images.get(1) : "",
                images.size() > 2 ? images.get(2) : "",
                images.size() > 3 ? images.get(3) : "",
                images.size() > 4 ? images.get(4) : ""
        );
    }

    // -------------------------------------------------------------------------
    // Misc
    // -------------------------------------------------------------------------

    private ImportJob loadJob(long companyId, long jobId) {
        return jobRepository.findByIdAndCompanyId(jobId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job not found: " + jobId));
    }

    private ImportJobResponse toResponse(ImportJob job) {
        int progress = job.getTotalRows() == 0
                ? (job.getStatus().isTerminal() ? 100 : 0)
                : (int) Math.min(100, Math.round((job.getProcessedRows() * 100.0) / job.getTotalRows()));
        return new ImportJobResponse(
                job.getId(),
                job.getCompanyId(),
                job.getUploadedBy(),
                job.getJobType(),
                job.getMode(),
                job.getStatus(),
                job.getFileName(),
                job.getTotalRows(),
                job.getProcessedRows(),
                job.getSuccessRows(),
                job.getErrorRows(),
                progress,
                job.getErrorReportS3Key() != null,
                job.getFailureReason(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    private ImportJobRowResponse toRowResponse(ImportJobRow row) {
        return new ImportJobRowResponse(
                row.getId(),
                row.getJobId(),
                row.getRowNumber(),
                row.getSku(),
                row.getErrorMessage()
        );
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
