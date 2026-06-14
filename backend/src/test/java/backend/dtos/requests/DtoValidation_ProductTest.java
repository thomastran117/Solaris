package backend.dtos.requests;

import backend.dtos.requests.product.BatchCreateProductsRequest;
import backend.dtos.requests.product.CreateProductRequest;
import backend.dtos.requests.product.CreateProductVariantRequest;
import backend.dtos.requests.product.UpdateProductRequest;
import backend.dtos.requests.product.UpdateProductVariantRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

class DtoValidation_ProductTest extends AbstractDtoValidationTest {

    // ─── CreateProductRequest ─────────────────────────────────────────────────

    @Test
    void createProduct_valid_noViolations() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget Pro");
        req.setPrice(new BigDecimal("49.99"));
        assertValid(req);
    }

    @Test
    void createProduct_blankName_violation() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("");
        req.setPrice(new BigDecimal("9.99"));
        assertViolation(req, "name");
    }

    @Test
    void createProduct_nullName_violation() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName(null);
        req.setPrice(new BigDecimal("9.99"));
        assertViolation(req, "name");
    }

    @Test
    void createProduct_nameTooLong_violation() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("A".repeat(256));
        req.setPrice(new BigDecimal("9.99"));
        assertViolation(req, "name");
    }

    @Test
    void createProduct_nameWithHtml_safeTextViolation() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("<script>alert('xss')</script>");
        req.setPrice(new BigDecimal("9.99"));
        assertViolation(req, "name");
    }

    @Test
    void createProduct_nullPrice_violation() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(null);
        assertViolation(req, "price");
    }

    @Test
    void createProduct_negativePrice_violation() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("-0.01"));
        assertViolation(req, "price");
    }

    @Test
    void createProduct_zeroPriceAllowed_noViolations() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Free Item");
        req.setPrice(BigDecimal.ZERO);
        assertValid(req);
    }

    @Test
    void createProduct_currencyWrongLength_violation() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setCurrency("US"); // must be exactly 3
        assertViolation(req, "currency");
    }

    @Test
    void createProduct_currency3Letters_noViolations() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setCurrency("GBP");
        assertValid(req);
    }

    @Test
    void createProduct_negativeStock_violation() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setStock(-1);
        assertViolation(req, "stock");
    }

    @Test
    void createProduct_zeroStockAllowed_noViolations() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setStock(0);
        assertValid(req);
    }

    @Test
    void createProduct_skuWithInvalidChars_safeIdentifierViolation() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setSku("SKU WITH SPACES"); // spaces not allowed
        assertViolation(req, "sku");
    }

    @Test
    void createProduct_skuValidPattern_noViolations() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setSku("SKU-001_v2");
        assertValid(req);
    }

    @Test
    void createProduct_descriptionWithScript_safeRichTextViolation() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setDescription("<script>evil()</script> description");
        assertViolation(req, "description");
    }

    @Test
    void createProduct_descriptionWithBold_noViolations() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("9.99"));
        req.setDescription("<b>Great</b> product with <i>features</i>.");
        assertValid(req);
    }

    @Test
    void createProduct_compareAtPriceLessThanPrice_assertTrueViolation() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("20.00"));
        req.setCompareAtPrice(new BigDecimal("10.00")); // must be GREATER than price
        assertViolation(req, "compareAtPriceValid");
    }

    @Test
    void createProduct_compareAtPriceGreaterThanPrice_noViolations() {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Widget");
        req.setPrice(new BigDecimal("20.00"));
        req.setCompareAtPrice(new BigDecimal("30.00"));
        assertValid(req);
    }

    // ─── CreateProductVariantRequest ──────────────────────────────────────────

    @Test
    void createVariant_valid_noViolations() {
        CreateProductVariantRequest req = new CreateProductVariantRequest();
        req.setPrice(new BigDecimal("19.99"));
        assertValid(req);
    }

    @Test
    void createVariant_nullPrice_violation() {
        CreateProductVariantRequest req = new CreateProductVariantRequest();
        req.setPrice(null);
        assertViolation(req, "price");
    }

    @Test
    void createVariant_negativeStock_violation() {
        CreateProductVariantRequest req = new CreateProductVariantRequest();
        req.setPrice(new BigDecimal("9.99"));
        req.setStock(-1);
        assertViolation(req, "stock");
    }

    @Test
    void createVariant_negativeLowStockThreshold_violation() {
        CreateProductVariantRequest req = new CreateProductVariantRequest();
        req.setPrice(new BigDecimal("9.99"));
        req.setLowStockThreshold(-1);
        assertViolation(req, "lowStockThreshold");
    }

    @Test
    void createVariant_option1WithHtml_violation() {
        CreateProductVariantRequest req = new CreateProductVariantRequest();
        req.setPrice(new BigDecimal("9.99"));
        req.setOption1("<b>Blue</b>"); // SafeText: HTML rejected
        assertViolation(req, "option1");
    }

    @Test
    void createVariant_skuWithSpaces_violation() {
        CreateProductVariantRequest req = new CreateProductVariantRequest();
        req.setPrice(new BigDecimal("9.99"));
        req.setSku("SKU 001"); // SafeIdentifier: spaces not allowed
        assertViolation(req, "sku");
    }

    @Test
    void createVariant_compareAtLessThanPrice_violation() {
        CreateProductVariantRequest req = new CreateProductVariantRequest();
        req.setPrice(new BigDecimal("20.00"));
        req.setCompareAtPrice(new BigDecimal("5.00"));
        assertViolation(req, "compareAtPriceValid");
    }

    @Test
    void updateVariant_negativeLowStockThreshold_violation() {
        UpdateProductVariantRequest req = new UpdateProductVariantRequest();
        req.setLowStockThreshold(-1);
        assertViolation(req, "lowStockThreshold");
    }

    // ─── UpdateProductRequest ─────────────────────────────────────────────────

    @Test
    void updateProduct_allNullFields_noViolations() {
        // UpdateProductRequest has no required fields — all are optional patches
        assertValid(new UpdateProductRequest());
    }

    @Test
    void updateProduct_nameTooLong_violation() {
        UpdateProductRequest req = new UpdateProductRequest();
        req.setName("N".repeat(256));
        assertViolation(req, "name");
    }

    @Test
    void updateProduct_nameWithHtml_violation() {
        UpdateProductRequest req = new UpdateProductRequest();
        req.setName("<img onerror=alert(1)>");
        assertViolation(req, "name");
    }

    // ─── BatchCreateProductsRequest ───────────────────────────────────────────

    @Test
    void batchCreate_emptyProducts_violation() {
        BatchCreateProductsRequest req = new BatchCreateProductsRequest();
        req.setProducts(List.of());
        assertViolation(req, "products");
    }

    @Test
    void batchCreate_nullProducts_violation() {
        BatchCreateProductsRequest req = new BatchCreateProductsRequest();
        req.setProducts(null);
        assertViolation(req, "products");
    }
}
