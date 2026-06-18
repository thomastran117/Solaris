package backend.services.impl.inventory;

import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.models.core.RestockRequest;
import backend.models.core.User;
import backend.models.enums.RestockStatus;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.RestockRequestRepository;
import backend.services.intf.OutboundWebhookEventPublisher;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockAlertServiceTest {

    private static final UUID PRODUCT_ID  = TestIds.uuid(1);
    private static final UUID VARIANT_ID  = TestIds.uuid(5);

    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private RestockRequestRepository restockRequestRepository;
    private EmailService emailService;
    private StockAlertService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        restockRequestRepository = mock(RestockRequestRepository.class);
        emailService = mock(EmailService.class);
        OutboundWebhookEventPublisher webhookPublisher = mock(OutboundWebhookEventPublisher.class);
        service = new StockAlertService(productRepository, variantRepository,
                restockRequestRepository, emailService, webhookPublisher,
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void checkAndAlert_breachedThresholdSendsEmailAndCreatesAutoRestock() {
        Product product = product(true, 6);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.findByIdWithCompanyOwner(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(restockRequestRepository.existsByProductIdAndVariantIsNullAndStatusIn(
                PRODUCT_ID, List.of(RestockStatus.PENDING, RestockStatus.IN_TRANSIT)))
                .thenReturn(false);

        service.checkAndAlert(PRODUCT_ID, "Desk", null, null, 1, 2);

        verify(emailService).sendLowStockAlertEmail(
                "owner@test.com", null, PRODUCT_ID, "Desk", null, null, 1, 2, false);

        ArgumentCaptor<RestockRequest> restockCaptor = ArgumentCaptor.forClass(RestockRequest.class);
        verify(restockRequestRepository).save(restockCaptor.capture());
        assertEquals(6, restockCaptor.getValue().getRequestedQty());
        assertEquals(RestockStatus.PENDING, restockCaptor.getValue().getStatus());
        assertTrue(restockCaptor.getValue().getSupplierNote().contains("stock reached 1 unit"));
    }

    @Test
    void checkAndAlert_whenThresholdNotBreachedDoesNothing() {
        Product product = product(false, null);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        service.checkAndAlert(PRODUCT_ID, "Desk", null, null, 5, 2);

        verify(productRepository, never()).findByIdWithCompanyOwner(PRODUCT_ID);
        verify(emailService, never()).sendLowStockAlertEmail(any(), any(), any(), any(), any(), any(), any(int.class), any(), any(boolean.class));
    }

    @Test
    void checkAndAlert_percentThresholdBreached_sendsEmail() {
        // 5/100 = 5% <= 10% threshold → breached; autoRestock disabled
        Product product = product(false, null);
        product.setLowStockThresholdPercent(10);
        product.setMaxStock(100);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.findByIdWithCompanyOwner(PRODUCT_ID)).thenReturn(Optional.of(product));

        service.checkAndAlert(PRODUCT_ID, "Desk", null, null, 5, null);

        verify(emailService).sendLowStockAlertEmail(
                "owner@test.com", null, PRODUCT_ID, "Desk", null, null, 5, null, false);
    }

    @Test
    void checkAndAlert_outOfStock_outOfStockFlagTrue() {
        Product product = product(false, null);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.findByIdWithCompanyOwner(PRODUCT_ID)).thenReturn(Optional.of(product));

        service.checkAndAlert(PRODUCT_ID, "Desk", null, null, 0, 5);

        verify(emailService).sendLowStockAlertEmail(
                "owner@test.com", null, PRODUCT_ID, "Desk", null, null, 0, 5, true);
    }

    @Test
    void checkAndAlert_activeRestockAlreadyExists_skipsCreatingNew() {
        Product product = product(true, 10);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.findByIdWithCompanyOwner(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(restockRequestRepository.existsByProductIdAndVariantIsNullAndStatusIn(
                PRODUCT_ID, List.of(RestockStatus.PENDING, RestockStatus.IN_TRANSIT)))
                .thenReturn(true); // active exists → skip

        service.checkAndAlert(PRODUCT_ID, "Desk", null, null, 1, 2);

        verify(restockRequestRepository, never()).save(any(RestockRequest.class));
    }

    @Test
    void checkAndAlert_variantPath_percentBreached_sendsEmailAndCreatesRestock() {
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT_ID);
        variant.setLowStockThresholdPercent(10);
        variant.setMaxStock(100);
        variant.setAutoRestockEnabled(true);
        variant.setAutoRestockQty(8);

        Product product = product(false, null);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
        when(productRepository.findByIdWithCompanyOwner(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(restockRequestRepository.existsByProductIdAndVariantIdAndStatusIn(
                PRODUCT_ID, VARIANT_ID, List.of(RestockStatus.PENDING, RestockStatus.IN_TRANSIT)))
                .thenReturn(false);

        service.checkAndAlert(PRODUCT_ID, "Desk", VARIANT_ID, "SKU-1", 5, null);

        verify(emailService).sendLowStockAlertEmail(
                "owner@test.com", null, PRODUCT_ID, "Desk", VARIANT_ID, "SKU-1", 5, null, false);

        ArgumentCaptor<RestockRequest> captor = ArgumentCaptor.forClass(RestockRequest.class);
        verify(restockRequestRepository).save(captor.capture());
        assertEquals(8, captor.getValue().getRequestedQty());
    }

    @Test
    void checkAndAlert_variantOutOfStock_logsOutOfStockVariantBranch() {
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT_ID);
        variant.setAutoRestockEnabled(false);

        Product product = product(false, null);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
        when(productRepository.findByIdWithCompanyOwner(PRODUCT_ID)).thenReturn(Optional.of(product));

        service.checkAndAlert(PRODUCT_ID, "Desk", VARIANT_ID, "SKU-1", 0, 1);

        verify(emailService).sendLowStockAlertEmail(
                "owner@test.com", null, PRODUCT_ID, "Desk", VARIANT_ID, "SKU-1", 0, 1, true);
    }

    @Test
    void checkAndAlert_variantNotFoundInRepo_skipsVariantSettings() {
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.empty());
        // No product load for threshold since variant lookup returned empty and both breaches are false
        service.checkAndAlert(PRODUCT_ID, "Desk", VARIANT_ID, "SKU-1", 5, null);

        // Neither quantity nor percent threshold breached → no email
        verify(emailService, never()).sendLowStockAlertEmail(any(), any(), any(), any(), any(), any(), any(int.class), any(), any(boolean.class));
    }

    @Test
    void checkAndAlertLocation_belowThreshold_logsLowStock() {
        // Should not throw; just logs warning
        service.checkAndAlertLocation(TestIds.uuid(10), "Warehouse A",
                PRODUCT_ID, "Desk", null, 3, 5);
        // No interactions needed — just coverage for the low-stock log branch
    }

    @Test
    void checkAndAlertLocation_outOfStock_logsOutOfStock() {
        service.checkAndAlertLocation(TestIds.uuid(10), "Warehouse A",
                PRODUCT_ID, "Desk", VARIANT_ID, 0, 5);
    }

    @Test
    void checkAndAlertLocation_aboveThreshold_doesNothing() {
        service.checkAndAlertLocation(TestIds.uuid(10), "Warehouse A",
                PRODUCT_ID, "Desk", null, 10, 5);
        // No logging; method returns early
    }

    @Test
    void checkAndAlertLocation_nullThreshold_doesNothing() {
        service.checkAndAlertLocation(TestIds.uuid(10), "Warehouse A",
                PRODUCT_ID, "Desk", null, 2, null);
    }

    private Product product(boolean autoRestockEnabled, Integer autoRestockQty) {
        User owner = new User();
        owner.setId(TestIds.uuid(2));
        owner.setEmail("owner@test.com");

        Company company = new Company();
        company.setId(TestIds.uuid(3));
        company.setOwner(owner);
        company.setName("ShopWave");

        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setCompany(company);
        product.setName("Desk");
        product.setPrice(BigDecimal.TEN);
        product.setCurrency("USD");
        product.setAutoRestockEnabled(autoRestockEnabled);
        product.setAutoRestockQty(autoRestockQty);
        return product;
    }
}
