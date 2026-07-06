package backend.integration.imports;

import backend.integration.AbstractIntegrationIT;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ImportIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ImportJobRepository importJobRepository;
    @Autowired private ImportJobRowRepository importJobRowRepository;

    @AfterEach
    void cleanImports() {
        try { jdbcTemplate.execute("DELETE FROM import_job_rows"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM import_jobs"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_images"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
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
        assertEquals(ImportJobStatus.PENDING, persisted.getStatus());
        assertEquals(ImportJobType.PRODUCT_UPSERT, persisted.getJobType());
        assertEquals(company.getId(), persisted.getCompanyId());
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
}
