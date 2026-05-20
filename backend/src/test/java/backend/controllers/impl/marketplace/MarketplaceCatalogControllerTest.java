package backend.controllers.impl.marketplace;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.product.CatalogSearchResponse;
import backend.dtos.responses.product.MarketplaceCatalogProductResponse;
import backend.dtos.responses.product.VendorStorefrontResponse;
import backend.events.activity.ActivityType;
import backend.events.activity.UserActivityEvent;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.products.ProductService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketplaceCatalogControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(2);
    private static final UUID PRODUCT_ID = TestIds.uuid(3);
    private static final UUID VENDOR_ID = TestIds.uuid(4);

    private ProductService productService;
    private ActivityEventPublisher activityEventPublisher;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        activityEventPublisher = mock(ActivityEventPublisher.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MarketplaceCatalogController(productService, activityEventPublisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void trackView_withDntHeaderSkipsPublish() throws Exception {
        mockMvc.perform(post("/marketplaces/" + MARKETPLACE_ID + "/catalog/products/" + PRODUCT_ID + "/view")
                        .header("DNT", "1"))
                .andExpect(status().isNoContent());

        verify(activityEventPublisher, never()).publish(any());
    }

    @Test
    void trackView_authenticatedUserPublishesEvent() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(post("/marketplaces/" + MARKETPLACE_ID + "/catalog/products/" + PRODUCT_ID + "/view")
                        .contentType("application/json")
                        .content("{\"sessionId\":\"sess-1\"}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<UserActivityEvent> eventCaptor = ArgumentCaptor.forClass(UserActivityEvent.class);
        verify(activityEventPublisher).publish(eventCaptor.capture());
        UserActivityEvent event = eventCaptor.getValue();
        assertEquals(USER_ID, event.userId());
        assertEquals("sess-1", event.sessionId());
        assertEquals(PRODUCT_ID, event.productId());
        assertEquals(ActivityType.VIEW, event.activityType());
    }

    @Test
    void trackView_unauthenticatedPrincipalPublishesAnonymousEvent() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("anonymousUser", null));

        mockMvc.perform(post("/marketplaces/" + MARKETPLACE_ID + "/catalog/products/" + PRODUCT_ID + "/view"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<UserActivityEvent> eventCaptor = ArgumentCaptor.forClass(UserActivityEvent.class);
        verify(activityEventPublisher).publish(eventCaptor.capture());
        UserActivityEvent event = eventCaptor.getValue();
        assertEquals(null, event.userId());
        assertEquals(PRODUCT_ID, event.productId());
    }

    @Test
    void searchCatalog_returnsPagedResultsAndPassesFilters() throws Exception {
        when(productService.searchMarketplaceCatalog(
                MARKETPLACE_ID, "desk", "Office", "Acme", new BigDecimal("10.00"), new BigDecimal("50.00"),
                true, VENDOR_ID, 1, 10, "createdAt", "asc"))
                .thenReturn(new CatalogSearchResponse(
                        new PageImpl<>(List.of(product()), PageRequest.of(1, 10), 1),
                        null
                ));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/products")
                        .param("q", "desk")
                        .param("category", "Office")
                        .param("brand", "Acme")
                        .param("minPrice", "10.00")
                        .param("maxPrice", "50.00")
                        .param("featured", "true")
                        .param("vendorId", VENDOR_ID.toString())
                        .param("page", "1")
                        .param("size", "10")
                        .param("sort", "createdAt")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(PRODUCT_ID.toString()));
    }

    @Test
    void searchCatalog_passesDefaultPagingAndSortParameters() throws Exception {
        when(productService.searchMarketplaceCatalog(
                MARKETPLACE_ID, null, null, null, null, null,
                null, null, 0, 20, "createdAt", "desc"))
                .thenReturn(new CatalogSearchResponse(new PageImpl<>(List.of()), null));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/products"))
                .andExpect(status().isOk());

        verify(productService).searchMarketplaceCatalog(
                MARKETPLACE_ID, null, null, null, null, null,
                null, null, 0, 20, "createdAt", "desc");
    }

    @Test
    void searchCatalog_passesDirectionWithoutStandaloneMethodValidation() throws Exception {
        when(productService.searchMarketplaceCatalog(
                MARKETPLACE_ID, null, null, null, null, null,
                null, null, 0, 20, "createdAt", "sideways"))
                .thenReturn(new CatalogSearchResponse(new PageImpl<>(List.of()), null));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/products")
                        .param("direction", "sideways"))
                .andExpect(status().isOk());

        verify(productService).searchMarketplaceCatalog(
                MARKETPLACE_ID, null, null, null, null, null,
                null, null, 0, 20, "createdAt", "sideways");
    }

    @Test
    void getProduct_returns200() throws Exception {
        when(productService.getMarketplaceProduct(MARKETPLACE_ID, PRODUCT_ID)).thenReturn(product());

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/products/" + PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorId").value(VENDOR_ID.toString()));
    }

    @Test
    void getProduct_unexpectedRuntimeReturns500() throws Exception {
        when(productService.getMarketplaceProduct(MARKETPLACE_ID, PRODUCT_ID))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/products/" + PRODUCT_ID))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getVendorStorefront_returns200() throws Exception {
        when(productService.getVendorStorefront(MARKETPLACE_ID, VENDOR_ID))
                .thenReturn(new VendorStorefrontResponse(
                        VENDOR_ID, MARKETPLACE_ID, "Acme", "desc", null, "GOLD", "ACTIVE", List.of(product()), 1L));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/vendors/" + VENDOR_ID + "/storefront"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorId").value(VENDOR_ID.toString()))
                .andExpect(jsonPath("$.featuredProducts[0].id").value(PRODUCT_ID.toString()));
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private MarketplaceCatalogProductResponse product() {
        return new MarketplaceCatalogProductResponse(
                PRODUCT_ID,
                TestIds.uuid(10),
                MARKETPLACE_ID,
                VENDOR_ID,
                "Acme",
                "GOLD",
                "Desk",
                null,
                "DESK-1",
                new BigDecimal("19.99"),
                null,
                "USD",
                "Office",
                "Acme",
                null,
                null,
                List.of(),
                List.of(),
                4,
                "ACTIVE",
                false,
                true,
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z"),
                null
        );
    }
}
