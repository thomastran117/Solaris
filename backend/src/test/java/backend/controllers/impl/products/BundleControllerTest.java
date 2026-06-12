package backend.controllers.impl.products;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.requests.product.BatchDeleteBundlesRequest;
import backend.dtos.requests.product.BatchUpdateBundlesRequest;
import backend.dtos.requests.product.BundleItemRequest;
import backend.dtos.requests.product.CreateBundleRequest;
import backend.dtos.requests.product.UpdateBundleRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.BundleResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.ProductStatus;
import backend.services.intf.products.BundleService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BundleControllerTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID USER_ID    = TestIds.uuid(2);
    private static final UUID BUNDLE_ID  = TestIds.uuid(3);
    private static final UUID PRODUCT_ID = TestIds.uuid(4);

    private BundleService bundleService;
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        bundleService = mock(BundleService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new BundleController(bundleService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET /companies/{companyId}/bundles ───────────────────────────────────

    @Test
    void listBundles_returns200() throws Exception {
        when(bundleService.listBundles(eq(COMPANY_ID), any(), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        mockMvc.perform(get("/companies/{cid}/bundles", COMPANY_ID))
                .andExpect(status().isOk());
    }

    // ─── POST /companies/{companyId}/bundles ──────────────────────────────────

    @Test
    void createBundle_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(bundleService.createBundle(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(makeBundleResponse());

        mockMvc.perform(post("/companies/{cid}/bundles", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalCreateBundleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Bundle"));
    }

    @Test
    void createBundle_badRequest_returns400() throws Exception {
        authenticateAs(USER_ID);
        when(bundleService.createBundle(any(), any(), any()))
                .thenThrow(new BadRequestException("price exceeds total"));

        mockMvc.perform(post("/companies/{cid}/bundles", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalCreateBundleRequest())))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /companies/{companyId}/bundles/{bundleId} ────────────────────────

    @Test
    void getBundle_returns200() throws Exception {
        when(bundleService.getBundle(COMPANY_ID, BUNDLE_ID)).thenReturn(makeBundleResponse());

        mockMvc.perform(get("/companies/{cid}/bundles/{bid}", COMPANY_ID, BUNDLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BUNDLE_ID.toString()));
    }

    @Test
    void getBundle_notFound_returns404() throws Exception {
        when(bundleService.getBundle(COMPANY_ID, BUNDLE_ID))
                .thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/companies/{cid}/bundles/{bid}", COMPANY_ID, BUNDLE_ID))
                .andExpect(status().isNotFound());
    }

    // ─── PATCH /companies/{companyId}/bundles/{bundleId} ─────────────────────

    @Test
    void updateBundle_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(bundleService.updateBundle(eq(COMPANY_ID), eq(BUNDLE_ID), eq(USER_ID), any()))
                .thenReturn(makeBundleResponse());

        mockMvc.perform(patch("/companies/{cid}/bundles/{bid}", COMPANY_ID, BUNDLE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateBundle_notFound_returns404() throws Exception {
        authenticateAs(USER_ID);
        when(bundleService.updateBundle(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(patch("/companies/{cid}/bundles/{bid}", COMPANY_ID, BUNDLE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /companies/{companyId}/bundles/{bundleId} ────────────────────

    @Test
    void deleteBundle_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/companies/{cid}/bundles/{bid}", COMPANY_ID, BUNDLE_ID))
                .andExpect(status().isNoContent());

        verify(bundleService).deleteBundle(COMPANY_ID, BUNDLE_ID, USER_ID);
    }

    // ─── GET /companies/{companyId}/bundles/compare ───────────────────────────

    @Test
    void compareBundles_twoIds_returns200() throws Exception {
        UUID b1 = TestIds.uuid(10);
        UUID b2 = TestIds.uuid(11);
        when(bundleService.compareBundles(eq(COMPANY_ID), anyList()))
                .thenReturn(List.of(makeBundleResponse(), makeBundleResponse()));

        mockMvc.perform(get("/companies/{cid}/bundles/compare", COMPANY_ID)
                        .param("ids", b1.toString(), b2.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void compareBundles_badRequest_returns400() throws Exception {
        when(bundleService.compareBundles(any(), anyList()))
                .thenThrow(new BadRequestException("requires 2–4 IDs"));

        mockMvc.perform(get("/companies/{cid}/bundles/compare", COMPANY_ID)
                        .param("ids", TestIds.uuid(10).toString()))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /companies/{companyId}/bundles/batch-update ─────────────────────

    @Test
    void batchUpdateBundles_returns200WithResults() throws Exception {
        authenticateAs(USER_ID);
        when(bundleService.batchUpdateBundles(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(List.of(makeBundleResponse()));

        BatchUpdateBundlesRequest req = new BatchUpdateBundlesRequest();
        req.setIds(List.of(BUNDLE_ID));
        req.setListed(true);

        mockMvc.perform(post("/companies/{cid}/bundles/batch-update", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(BUNDLE_ID.toString()));
    }

    @Test
    void batchUpdateBundles_scheduledStatus_returns400() throws Exception {
        authenticateAs(USER_ID);
        when(bundleService.batchUpdateBundles(any(), any(), any()))
                .thenThrow(new BadRequestException("SCHEDULED not allowed in bulk"));

        BatchUpdateBundlesRequest req = new BatchUpdateBundlesRequest();
        req.setIds(List.of(BUNDLE_ID));
        req.setStatus(ProductStatus.SCHEDULED);

        mockMvc.perform(post("/companies/{cid}/bundles/batch-update", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /companies/{companyId}/bundles/batch-delete ─────────────────────

    @Test
    void batchDeleteBundles_returns204() throws Exception {
        authenticateAs(USER_ID);

        BatchDeleteBundlesRequest req = new BatchDeleteBundlesRequest();
        req.setIds(List.of(BUNDLE_ID));

        mockMvc.perform(post("/companies/{cid}/bundles/batch-delete", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(bundleService).batchDeleteBundles(eq(COMPANY_ID), eq(USER_ID), any());
    }

    @Test
    void batchDeleteBundles_notFound_returns404() throws Exception {
        authenticateAs(USER_ID);
        doThrow(new ResourceNotFoundException("not found"))
                .when(bundleService).batchDeleteBundles(any(), any(), any());

        BatchDeleteBundlesRequest req = new BatchDeleteBundlesRequest();
        req.setIds(List.of(BUNDLE_ID));

        mockMvc.perform(post("/companies/{cid}/bundles/batch-delete", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private BundleResponse makeBundleResponse() {
        return new BundleResponse(
                BUNDLE_ID, COMPANY_ID, "Test Bundle", null, null,
                new BigDecimal("20.00"), null, "USD",
                ProductStatus.ACTIVE.name(), false, false, null,
                List.of(), Instant.now(), Instant.now());
    }

    private CreateBundleRequest minimalCreateBundleRequest() {
        BundleItemRequest itemReq = new BundleItemRequest();
        itemReq.setProductId(PRODUCT_ID);
        itemReq.setQuantity(1);

        CreateBundleRequest req = new CreateBundleRequest();
        req.setName("Test Bundle");
        req.setItems(List.of(itemReq));
        return req;
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
