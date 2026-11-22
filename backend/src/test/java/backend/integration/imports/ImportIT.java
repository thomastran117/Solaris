package backend.integration.imports;

import backend.integration.fullinfra.AbstractSearchKafkaIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.ImportJob;
import backend.models.core.ImportJobRow;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.ImportJobStatus;
import backend.models.enums.ImportJobType;
import backend.models.enums.ProductStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.ImportJobRepository;
import backend.repositories.ImportJobRowRepository;
import backend.repositories.ProductRepository;
import backend.services.intf.StorageService;
import backend.dtos.responses.upload.PresignUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Runs against live Elasticsearch + Kafka (via {@link AbstractSearchKafkaIT}) so the import
 * worker is exercised end-to-end: {@code POST /imports} persists a PENDING job, publishes to
 * the {@code import-jobs} topic after commit, and {@code ImportJobConsumer} consumes it on a
 * real listener thread and mutates PostgreSQL.
 *
 * <p>Previously this class sat on {@code AbstractIntegrationIT}, where
 * {@code spring.kafka.listener.auto-startup=false} meant no consumer ever ran — the suite could
 * only assert that a job row was created, never that anything processed it. A consumer that
 * silently stopped upserting products would not have failed a single test.
 *
 * <p>{@link StorageService} is the one dependency that stays mocked. The worker reads the CSV
 * through {@code storageService.openObject(csvS3Key)}, and the real S3-backed implementation
 * would reach the (also mocked) {@code S3Client}. Mocking at the service level additionally
 * gives <b>per-key</b> control: only keys registered in {@link #csvByKey} yield bytes, so the
 * jobs created incidentally by the authorization/validation tests below fail fast at the parse
 * stage and cannot write product rows into a neighbouring test's fixture.
 */
class ImportIT extends AbstractSearchKafkaIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ImportJobRepository importJobRepository;
    @Autowired private ImportJobRowRepository importJobRowRepository;

    @MockitoBean private StorageService storageService;

    /** CSV payloads the worker is allowed to read, keyed by S3 key. */
    private final Map<String, byte[]> csvByKey = new HashMap<>();

    @BeforeEach
    void stubStorage() {
        csvByKey.clear();

        // A fresh stream per call: the worker consumes it, and a Kafka re-delivery would
        // otherwise be handed an already-exhausted ByteArrayInputStream.
        when(storageService.openObject(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            byte[] csv = csvByKey.get(key);
            if (csv == null) {
                throw new UncheckedIOException(new IOException("No such object: " + key));
            }
            return new ByteArrayInputStream(csv);
        });

        // Export and error-report writes go through the same service; the endpoints only
        // surface the returned key/URL, so a canned response is enough.
        when(storageService.putBytes(any(), any(), anyString(), any()))
                .thenAnswer(invocation -> new PresignUploadResponse(
                        "https://s3.test/upload", "https://s3.test/file",
                        "imports/generated/" + UUID.randomUUID() + ".csv", 600));
        when(storageService.presignDownload(anyString(), anyInt()))
                .thenReturn("https://s3.test/download.csv");
    }

    /** Registers CSV content the worker may read for {@code key}, and returns the key. */
    private String registerCsv(String key, String csv) {
        csvByKey.put(key, csv.getBytes(StandardCharsets.UTF_8));
        return key;
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Import Co " + UUID.randomUUID());
        return companyRepository.save(c);
    }

    private void addMember(User user, Company company, CompanyRole role) {
        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(user);
        m.setRole(role);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);
    }

    private Product createProduct(Company company, String sku) {
        Product p = new Product();
        p.setCompany(company);
        p.setName("Product " + UUID.randomUUID());
        p.setSku(sku);
        p.setPrice(BigDecimal.valueOf(9.99));
        p.setStatus(ProductStatus.ACTIVE);
        p.setStock(10);
        return productRepository.save(p);
    }

    private ImportJob createJobDirect(UUID companyId, UUID uploadedBy, ImportJobType jobType, ImportJobStatus status) {
        ImportJob job = new ImportJob();
        job.setCompanyId(companyId);
        job.setUploadedBy(uploadedBy);
        job.setJobType(jobType);
        job.setStatus(status);
        job.setCsvS3Key("imports/csv/" + UUID.randomUUID() + ".csv");
        job.setFileName("products.csv");
        return importJobRepository.save(job);
    }

    private ImportJobRow createJobRowDirect(UUID jobId, int rowNumber, String sku, String errorMessage) {
        ImportJobRow row = new ImportJobRow();
        row.setJobId(jobId);
        row.setRowNumber(rowNumber);
        row.setSku(sku);
        row.setErrorMessage(errorMessage);
        return importJobRowRepository.save(row);
    }

    private String createJobBody(ImportJobType jobType, String csvS3Key) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobType", jobType.name());
        body.put("csvS3Key", csvS3Key);
        body.put("fileName", "products.csv");
        return objectMapper.writeValueAsString(body);
    }

    private String attachImagesBody(String sku, String imageUrl, int displayOrder) throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sku", sku);
        item.put("imageUrl", imageUrl);
        item.put("displayOrder", displayOrder);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        return objectMapper.writeValueAsString(body);
    }

    // ── POST /companies/{companyId}/imports ───────────────────────────────────

    @Test
    void createJob_owner_productUpsert_returns201Pending() throws Exception {
        User owner = createActiveUser("import-create-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        MvcResult result = mockMvc.perform(post("/companies/{companyId}/imports", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobBody(ImportJobType.PRODUCT_UPSERT, "imports/csv/upsert.csv")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.data.jobType").value("PRODUCT_UPSERT"))
                .andExpect(jsonPath("$.data.mode").value("UPSERT"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.progressPercent").value(0))
                .andExpect(jsonPath("$.data.hasErrorReport").value(false))
                .andReturn();

        UUID jobId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
        ImportJob persisted = importJobRepository.findById(jobId).orElseThrow();
        assertEquals(ImportJobType.PRODUCT_UPSERT, persisted.getJobType());
        assertEquals(company.getId(), persisted.getCompanyId());
        // The row's *status* is deliberately not asserted here. The response body above is
        // serialized inside the creating transaction, before ImportJobPublisher's AFTER_COMMIT
        // hook puts the job on the topic, so "PENDING" is deterministic there -- but by the time
        // this assertion runs the live consumer may already have claimed PENDING -> PARSING.
        // The status lifecycle is covered deterministically by the two consumer tests below.
    }

    @Test
    void createJob_manager_inventorySync_returns201() throws Exception {
        User owner = createActiveUser("import-create-mgr-owner@example.com", "Password1!");
        User manager = createActiveUser("import-create-mgr@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(manager, company, CompanyRole.MANAGER);

        mockMvc.perform(post("/companies/{companyId}/imports", company.getId())
                        .header("Authorization", bearer(accessTokenFor(manager)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobBody(ImportJobType.INVENTORY_SYNC, "imports/csv/inventory.csv")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.jobType").value("INVENTORY_SYNC"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void createJob_employeeWithoutManageProducts_returns403() throws Exception {
        User owner = createActiveUser("import-create-403-owner@example.com", "Password1!");
        User employee = createActiveUser("import-create-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(post("/companies/{companyId}/imports", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobBody(ImportJobType.PRODUCT_UPSERT, "imports/csv/upsert.csv")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createJob_missingCsvS3Key_returns400() throws Exception {
        User owner = createActiveUser("import-create-400-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobType", "PRODUCT_UPSERT");

        mockMvc.perform(post("/companies/{companyId}/imports", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createJob_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("import-create-401-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{companyId}/imports", company.getId())
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobBody(ImportJobType.PRODUCT_UPSERT, "imports/csv/upsert.csv")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createJob_userWithoutMembership_returns403() throws Exception {
        User owner = createActiveUser("import-create-nomember-owner@example.com", "Password1!");
        User outsider = createActiveUser("import-create-nomember-outsider@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{companyId}/imports", company.getId())
                        .header("Authorization", bearer(accessTokenFor(outsider)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobBody(ImportJobType.PRODUCT_UPSERT, "imports/csv/upsert.csv")))
                .andExpect(status().isForbidden());
    }

    // ── GET /companies/{companyId}/imports ────────────────────────────────────

    @Test
    void listJobs_returnsEmptyWhenNone() throws Exception {
        User owner = createActiveUser("import-list-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{companyId}/imports", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void listJobs_returnsCreatedJobs() throws Exception {
        User owner = createActiveUser("import-list-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        ImportJob job1 = createJobDirect(company.getId(), owner.getId(), ImportJobType.PRODUCT_UPSERT, ImportJobStatus.PENDING);
        ImportJob job2 = createJobDirect(company.getId(), owner.getId(), ImportJobType.INVENTORY_SYNC, ImportJobStatus.COMPLETED);

        mockMvc.perform(get("/companies/{companyId}/imports", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.meta.totalElements").value(2))
                .andExpect(jsonPath("$.data[*].id",
                        containsInAnyOrder(job1.getId().toString(), job2.getId().toString())));
    }

    @Test
    void listJobs_employeeCanList() throws Exception {
        User owner = createActiveUser("import-list-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("import-list-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        createJobDirect(company.getId(), owner.getId(), ImportJobType.PRODUCT_UPSERT, ImportJobStatus.PENDING);

        mockMvc.perform(get("/companies/{companyId}/imports", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void listJobs_userWithoutMembership_returns403() throws Exception {
        User owner = createActiveUser("import-list-403-owner@example.com", "Password1!");
        User outsider = createActiveUser("import-list-403-outsider@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{companyId}/imports", company.getId())
                        .header("Authorization", bearer(accessTokenFor(outsider)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    // ── GET /companies/{companyId}/imports/{jobId} ────────────────────────────

    @Test
    void getJob_returnsDetailsWithProgressPercent() throws Exception {
        User owner = createActiveUser("import-get-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        ImportJob job = createJobDirect(company.getId(), owner.getId(), ImportJobType.PRODUCT_UPSERT, ImportJobStatus.PROCESSING);
        job.setTotalRows(10);
        job.setProcessedRows(5);
        importJobRepository.save(job);

        mockMvc.perform(get("/companies/{companyId}/imports/{jobId}", company.getId(), job.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(job.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.totalRows").value(10))
                .andExpect(jsonPath("$.data.processedRows").value(5))
                .andExpect(jsonPath("$.data.progressPercent").value(50));
    }

    @Test
    void getJob_unknownJobId_returns404() throws Exception {
        User owner = createActiveUser("import-get-404-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{companyId}/imports/{jobId}", company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getJob_jobFromOtherCompany_returns404() throws Exception {
        User owner1 = createActiveUser("import-get-cross-owner1@example.com", "Password1!");
        User owner2 = createActiveUser("import-get-cross-owner2@example.com", "Password1!");
        Company company1 = createCompany(owner1);
        Company company2 = createCompany(owner2);
        addMember(owner1, company1, CompanyRole.OWNER);
        addMember(owner2, company2, CompanyRole.OWNER);
        ImportJob job = createJobDirect(company2.getId(), owner2.getId(), ImportJobType.PRODUCT_UPSERT, ImportJobStatus.PENDING);

        mockMvc.perform(get("/companies/{companyId}/imports/{jobId}", company1.getId(), job.getId())
                        .header("Authorization", bearer(accessTokenFor(owner1)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getJob_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("import-get-401-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        ImportJob job = createJobDirect(company.getId(), owner.getId(), ImportJobType.PRODUCT_UPSERT, ImportJobStatus.PENDING);

        mockMvc.perform(get("/companies/{companyId}/imports/{jobId}", company.getId(), job.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{companyId}/imports/{jobId}/errors ─────────────────────

    @Test
    void listErrors_returnsEmptyWhenNoErrorRows() throws Exception {
        User owner = createActiveUser("import-errors-empty-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        ImportJob job = createJobDirect(company.getId(), owner.getId(), ImportJobType.PRODUCT_UPSERT, ImportJobStatus.COMPLETED);

        mockMvc.perform(get("/companies/{companyId}/imports/{jobId}/errors", company.getId(), job.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void listErrors_returnsPagedRowResponses() throws Exception {
        User owner = createActiveUser("import-errors-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        ImportJob job = createJobDirect(company.getId(), owner.getId(), ImportJobType.PRODUCT_UPSERT, ImportJobStatus.COMPLETED_WITH_ERRORS);
        createJobRowDirect(job.getId(), 1, "SKU-A", "Price is required");
        createJobRowDirect(job.getId(), 2, "SKU-B", "Invalid stock value");

        mockMvc.perform(get("/companies/{companyId}/imports/{jobId}/errors", company.getId(), job.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.meta.totalElements").value(2))
                .andExpect(jsonPath("$.data[0].rowNumber").value(1))
                .andExpect(jsonPath("$.data[0].sku").value("SKU-A"))
                .andExpect(jsonPath("$.data[0].errorMessage").value("Price is required"))
                .andExpect(jsonPath("$.data[1].rowNumber").value(2))
                .andExpect(jsonPath("$.data[1].sku").value("SKU-B"));
    }

    // ── GET /companies/{companyId}/imports/{jobId}/error-report ──────────────

    @Test
    void getErrorReport_noStoredKey_returns404() throws Exception {
        User owner = createActiveUser("import-report-404-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        ImportJob job = createJobDirect(company.getId(), owner.getId(), ImportJobType.PRODUCT_UPSERT, ImportJobStatus.PENDING);

        mockMvc.perform(get("/companies/{companyId}/imports/{jobId}/error-report", company.getId(), job.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getErrorReport_withStoredKey_returnsDownloadUrl() throws Exception {
        User owner = createActiveUser("import-report-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        ImportJob job = createJobDirect(company.getId(), owner.getId(), ImportJobType.PRODUCT_UPSERT, ImportJobStatus.COMPLETED_WITH_ERRORS);
        job.setErrorReportS3Key("imports/errors/" + job.getId() + ".csv");
        importJobRepository.save(job);

        mockMvc.perform(get("/companies/{companyId}/imports/{jobId}/error-report", company.getId(), job.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url", not(emptyOrNullString())))
                .andExpect(jsonPath("$.data.expiresIn").value(600));
    }

    // ── POST /companies/{companyId}/imports/export ────────────────────────────

    @Test
    void exportCatalogue_owner_returnsDownloadUrl() throws Exception {
        User owner = createActiveUser("import-export-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{companyId}/imports/export", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url", not(emptyOrNullString())))
                .andExpect(jsonPath("$.data.expiresIn").value(600));
    }

    @Test
    void exportCatalogue_employeeWithoutManageProducts_returns403() throws Exception {
        User owner = createActiveUser("import-export-403-owner@example.com", "Password1!");
        User employee = createActiveUser("import-export-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(post("/companies/{companyId}/imports/export", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    // ── POST /companies/{companyId}/imports/attach-images ─────────────────────

    @Test
    void attachImages_attachesMatchingSkuAndReturnsCount() throws Exception {
        User owner = createActiveUser("import-attach-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        createProduct(company, "import-sku-attach");

        mockMvc.perform(post("/companies/{companyId}/imports/attach-images", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attachImagesBody("import-sku-attach", "https://cdn.example.com/img.png", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attached").value(1));

        // The image must actually be attached to the matched product in the database.
        Integer imageRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_images WHERE image_url = 'https://cdn.example.com/img.png'",
                Integer.class);
        assertEquals(1, imageRows, "Image should be attached to the product");
    }

    @Test
    void attachImages_skuNotFound_returnsZeroAttached() throws Exception {
        User owner = createActiveUser("import-attach-zero-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        createProduct(company, "import-sku-other");

        mockMvc.perform(post("/companies/{companyId}/imports/attach-images", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attachImagesBody("import-sku-missing", "https://cdn.example.com/img.png", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attached").value(0));
    }

    @Test
    void attachImages_emptyItems_returns400() throws Exception {
        User owner = createActiveUser("import-attach-400-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of());

        mockMvc.perform(post("/companies/{companyId}/imports/attach-images", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void attachImages_employeeWithoutManageProducts_returns403() throws Exception {
        User owner = createActiveUser("import-attach-403-owner@example.com", "Password1!");
        User employee = createActiveUser("import-attach-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        createProduct(company, "import-sku-emp");

        mockMvc.perform(post("/companies/{companyId}/imports/attach-images", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attachImagesBody("import-sku-emp", "https://cdn.example.com/img.png", 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    void attachImages_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("import-attach-401-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        createProduct(company, "import-sku-401");

        mockMvc.perform(post("/companies/{companyId}/imports/attach-images", company.getId())
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attachImagesBody("import-sku-401", "https://cdn.example.com/img.png", 0)))
                .andExpect(status().isUnauthorized());
    }

    // -- Kafka consumer -> database --------------------------------------------
    // The tests above stop at the HTTP boundary. These two drive the whole path:
    // POST /imports -> ImportJobRequestedEvent (AFTER_COMMIT) -> import-jobs Kafka topic ->
    // ImportJobConsumer -> ImportServiceImpl.processJob -> product rows in PostgreSQL.

    @Test
    void createJob_productUpsert_consumedByWorker_persistsProducts() throws Exception {
        User owner = createActiveUser("import-consume-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        // Built already-uppercase on purpose. SanitizationService.normalizeCode uppercases every
        // SKU on the way in, while UUID.randomUUID() renders lowercase hex - so a mixed-case SKU
        // is stored in a different form than the CSV carried, and findAllBySkuInAndCompanyId is an
        // exact match (SKUs deliberately keep the casing the merchant entered). Normalizing here
        // keeps the fixture and the assertion talking about the same string.
        String skuA = ("CONSUMED-A-" + UUID.randomUUID()).toUpperCase(Locale.ROOT);
        String skuB = ("CONSUMED-B-" + UUID.randomUUID()).toUpperCase(Locale.ROOT);
        // Header casing is intentionally mixed -- the parser matches headers case-insensitively.
        String csvKey = registerCsv("imports/csv/consumed-upsert.csv", String.join("\n",
                "SKU,Name,price,Stock",
                skuA + ",Consumed Widget A,12.50,7",
                skuB + ",Consumed Widget B,34.00,3"));

        MvcResult result = mockMvc.perform(post("/companies/{companyId}/imports", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobBody(ImportJobType.PRODUCT_UPSERT, csvKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        UUID jobId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // Poll until the worker reaches a terminal status rather than sleeping a fixed duration.
        // Awaiting here (not in @AfterEach) also matters because @MockitoBean stubs are reset
        // between test methods -- an in-flight consumer would lose its CSV stub.
        ImportJob finished = await(Duration.ofSeconds(60),
                () -> importJobRepository.findById(jobId).orElse(null),
                job -> job != null && job.getStatus().isTerminal());

        assertNotNull(finished, "Import job row should still exist after processing");
        assertEquals(ImportJobStatus.COMPLETED, finished.getStatus(),
                "Worker should have completed the job without row errors; failureReason="
                        + finished.getFailureReason());
        assertEquals(2, finished.getTotalRows(), "Both CSV rows should have been parsed");
        assertEquals(2, finished.getSuccessRows(), "Both rows should have been upserted");
        assertEquals(0, finished.getErrorRows(), "No row should have failed validation");

        // The actual point of the test: the consumer mutated PostgreSQL.
        List<Product> imported =
                productRepository.findAllBySkuInAndCompanyId(List.of(skuA, skuB), company.getId());
        assertEquals(2, imported.size(),
                "Both CSV rows should have been persisted as products by the Kafka consumer");

        Map<String, Product> bySku = new LinkedHashMap<>();
        for (Product p : imported) {
            bySku.put(p.getSku(), p);
        }

        Product a = bySku.get(skuA);
        assertNotNull(a, "Product for " + skuA + " should exist");
        assertEquals("Consumed Widget A", a.getName());
        assertEquals(0, new BigDecimal("12.50").compareTo(a.getPrice()), "Price should match the CSV");
        assertEquals(7, a.getStock().intValue(), "Stock should match the CSV");

        Product b = bySku.get(skuB);
        assertNotNull(b, "Product for " + skuB + " should exist");
        assertEquals("Consumed Widget B", b.getName());
        assertEquals(0, new BigDecimal("34.00").compareTo(b.getPrice()), "Price should match the CSV");
        assertEquals(3, b.getStock().intValue(), "Stock should match the CSV");
    }

    @Test
    void createJob_consumerMarksJobFailed_whenCsvCannotBeRead() throws Exception {
        User owner = createActiveUser("import-consume-fail@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        // Deliberately NOT registered in csvByKey, so openObject throws for this key.
        MvcResult result = mockMvc.perform(post("/companies/{companyId}/imports", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobBody(ImportJobType.PRODUCT_UPSERT, "imports/csv/missing-object.csv")))
                .andExpect(status().isCreated())
                .andReturn();

        UUID jobId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());

        ImportJob finished = await(Duration.ofSeconds(60),
                () -> importJobRepository.findById(jobId).orElse(null),
                job -> job != null && job.getStatus().isTerminal());

        assertNotNull(finished, "Import job row should still exist after the failed run");
        assertEquals(ImportJobStatus.FAILED, finished.getStatus(),
                "An unreadable CSV should leave the job FAILED, not stuck mid-flight");
        assertNotNull(finished.getFailureReason(),
                "The failure should be recorded on the job row so operators can query it");
        assertTrue(productRepository.findAllBySkuInAndCompanyId(
                        List.of("missing-object"), company.getId()).isEmpty(),
                "A failed parse should not have created any products");
    }
}
