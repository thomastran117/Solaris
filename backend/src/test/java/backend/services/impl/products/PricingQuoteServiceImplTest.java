package backend.services.impl.products;

import backend.dtos.requests.pricing.PricingQuoteRequest;
import backend.dtos.responses.pricing.PricingQuoteResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductBundle;
import backend.models.core.ProductVariant;
import backend.models.enums.ProductStatus;
import backend.repositories.BundleRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.UserRepository;
import backend.services.intf.pricing.PricingEngine;
import backend.services.pricing.AppliedPromotion;
import backend.services.pricing.LineBreakdown;
import backend.services.pricing.PricingResult;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PricingQuoteServiceImplTest {

    private static final UUID PRODUCT_ID = TestIds.uuid(1);
    private static final UUID BUNDLE_ID  = TestIds.uuid(2);
    private static final UUID VARIANT_ID = TestIds.uuid(3);
    private static final UUID COMPANY_ID = TestIds.uuid(4);
    private static final UUID USER_ID    = TestIds.uuid(5);

    private PricingEngine           pricingEngine;
    private ProductRepository       productRepository;
    private ProductVariantRepository variantRepository;
    private BundleRepository        bundleRepository;
    private UserRepository          userRepository;

    private PricingQuoteServiceImpl service;

    @BeforeEach
    void setUp() {
        pricingEngine     = mock(PricingEngine.class);
        productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        bundleRepository  = mock(BundleRepository.class);
        userRepository    = mock(UserRepository.class);

        service = new PricingQuoteServiceImpl(
                pricingEngine, productRepository, variantRepository,
                bundleRepository, userRepository);
    }

    @Test
    void quote_emptyItems_throwsBadRequestException() {
        PricingQuoteRequest req = new PricingQuoteRequest();
        req.setItems(List.of());

        assertThrows(BadRequestException.class, () -> service.quote(req, null));
    }

    @Test
    void quote_bundleNotFound_throwsResourceNotFoundException() {
        PricingQuoteRequest.Item item = new PricingQuoteRequest.Item();
        item.setBundleId(BUNDLE_ID);
        item.setQuantity(1);
        PricingQuoteRequest req = request(item);

        when(bundleRepository.findById(BUNDLE_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.quote(req, null));
    }

    @Test
    void quote_bundleNotActive_throwsBadRequestException() {
        PricingQuoteRequest.Item item = new PricingQuoteRequest.Item();
        item.setBundleId(BUNDLE_ID);
        item.setQuantity(1);
        PricingQuoteRequest req = request(item);

        ProductBundle bundle = bundle();
        bundle.setStatus(ProductStatus.DRAFT);
        when(bundleRepository.findById(BUNDLE_ID)).thenReturn(Optional.of(bundle));

        assertThrows(BadRequestException.class, () -> service.quote(req, null));
    }

    @Test
    void quote_productNotFound_throwsResourceNotFoundException() {
        PricingQuoteRequest req = request(productItem(PRODUCT_ID, null));

        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.quote(req, null));
    }

    @Test
    void quote_productNotActive_throwsBadRequestException() {
        PricingQuoteRequest req = request(productItem(PRODUCT_ID, null));

        Product product = product();
        product.setStatus(ProductStatus.DRAFT);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        assertThrows(BadRequestException.class, () -> service.quote(req, null));
    }

    @Test
    void quote_productWithVariant_usesVariantPrice() {
        PricingQuoteRequest req = request(productItem(PRODUCT_ID, VARIANT_ID));

        Product product = product();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT_ID);
        variant.setPrice(new BigDecimal("29.99"));
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));
        when(pricingEngine.quote(any())).thenReturn(emptyResult());

        service.quote(req, null);

        verify(variantRepository).findByIdAndProductId(VARIANT_ID, PRODUCT_ID);
    }

    @Test
    void quote_productWithoutVariant_usesProductPrice() {
        PricingQuoteRequest req = request(productItem(PRODUCT_ID, null));

        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(pricingEngine.quote(any())).thenReturn(emptyResult());

        service.quote(req, null);

        verify(variantRepository, never()).findByIdAndProductId(any(), any());
    }

    @Test
    void quote_anonymousUser_doesNotCallUserRepository() {
        PricingQuoteRequest req = request(productItem(PRODUCT_ID, null));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(pricingEngine.quote(any())).thenReturn(emptyResult());

        service.quote(req, null);

        verify(userRepository, never()).findSegmentIdsByUserId(any());
    }

    @Test
    void quote_authenticatedUser_callsUserRepositoryForSegments() {
        PricingQuoteRequest req = request(productItem(PRODUCT_ID, null));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(userRepository.findSegmentIdsByUserId(USER_ID)).thenReturn(List.of());
        when(pricingEngine.quote(any())).thenReturn(emptyResult());

        service.quote(req, USER_ID);

        verify(userRepository).findSegmentIdsByUserId(USER_ID);
    }

    @Test
    void quote_nullCurrency_defaultsToUSD() {
        PricingQuoteRequest req = request(productItem(PRODUCT_ID, null));
        req.setCurrency(null);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(pricingEngine.quote(any())).thenReturn(emptyResult());

        PricingQuoteResponse resp = service.quote(req, null);

        assert resp.currency().equals("USD");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private PricingQuoteRequest request(PricingQuoteRequest.Item item) {
        PricingQuoteRequest req = new PricingQuoteRequest();
        req.setItems(List.of(item));
        return req;
    }

    private PricingQuoteRequest.Item productItem(UUID productId, UUID variantId) {
        PricingQuoteRequest.Item item = new PricingQuoteRequest.Item();
        item.setProductId(productId);
        item.setVariantId(variantId);
        item.setQuantity(1);
        return item;
    }

    private Product product() {
        Company company = new Company();
        company.setId(COMPANY_ID);

        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setStatus(ProductStatus.ACTIVE);
        p.setListed(true);
        p.setPrice(new BigDecimal("49.99"));
        p.setCompany(company);
        return p;
    }

    private ProductBundle bundle() {
        Company company = new Company();
        company.setId(COMPANY_ID);

        ProductBundle b = new ProductBundle();
        b.setId(BUNDLE_ID);
        b.setStatus(ProductStatus.ACTIVE);
        b.setListed(true);
        b.setPrice(new BigDecimal("99.00"));
        b.setCompany(company);
        return b;
    }

    private PricingResult emptyResult() {
        return new PricingResult(
                List.of(), List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of());
    }
}
