package backend.services.impl.inventory;

import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.RestockRequest;
import backend.models.core.User;
import backend.models.enums.RestockStatus;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.RestockRequestRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockAlertServiceTest {

    private static final UUID PRODUCT_ID = TestIds.uuid(1);

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
        service = new StockAlertService(productRepository, variantRepository, restockRequestRepository, emailService);
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
