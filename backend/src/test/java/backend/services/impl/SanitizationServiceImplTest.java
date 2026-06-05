package backend.services.impl;

import backend.dtos.requests.inventory.AdjustStockRequest;
import backend.dtos.requests.inventory.BulkAdjustItem;
import backend.dtos.requests.inventory.BulkAdjustRequest;
import backend.dtos.requests.inventory.CreateLocationRequest;
import backend.dtos.requests.inventory.CreateRestockRequest;
import backend.dtos.requests.inventory.UpdateLocationRequest;
import backend.dtos.requests.inventory.UpdateRestockRequest;
import backend.dtos.requests.product.AddProductImageRequest;
import backend.dtos.requests.product.BatchCreateProductsRequest;
import backend.dtos.requests.product.CreateProductOptionRequest;
import backend.dtos.requests.product.CreateProductRequest;
import backend.dtos.requests.product.CreateProductVariantRequest;
import backend.dtos.requests.product.SetProductAttributesRequest;
import backend.dtos.requests.product.UpdateProductOptionRequest;
import backend.dtos.requests.product.UpdateProductRequest;
import backend.dtos.requests.product.UpdateProductVariantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SanitizationServiceImplTest {

    private SanitizationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SanitizationServiceImpl();
        service.afterPropertiesSet();
    }

    // ---- normalizeText ----

    @Test
    void normalizeText_trimsAndCollapsesWhitespace() {
        assertEquals("Widget Pro", service.normalizeText("   Widget   Pro  "));
        assertEquals("a b c", service.normalizeText("a\tb\nc"));
    }

    @Test
    void normalizeText_nullPassthrough() {
        assertNull(service.normalizeText(null));
    }

    // ---- normalizeRichText ----

    @Test
    void normalizeRichText_trimsButPreservesInternalNewlines() {
        assertEquals("line one\nline two", service.normalizeRichText("  line one\nline two  "));
    }

    // ---- normalizeCode / normalizeCategory ----

    @Test
    void normalizeCode_upperCases() {
        assertEquals("USD", service.normalizeCode(" usd "));
    }

    @Test
    void normalizeCategory_lowerCases() {
        assertEquals("electronics", service.normalizeCategory(" Electronics "));
    }

    // ---- isSafePlainText ----

    @Test
    void isSafePlainText_acceptsClean() {
        assertTrue(service.isSafePlainText("Blue T-shirt (XL)"));
        assertTrue(service.isSafePlainText(null));
        assertTrue(service.isSafePlainText(""));
    }

    @Test
    void isSafePlainText_rejectsHtml() {
        assertFalse(service.isSafePlainText("<script>alert(1)</script>"));
        assertFalse(service.isSafePlainText("name with <b>tag</b>"));
    }

    @Test
    void isSafePlainText_rejectsControlChars() {
        assertFalse(service.isSafePlainText("ctrl\u0007char"));
        assertFalse(service.isSafePlainText("nul\u0000byte"));
    }

    @Test
    void isSafePlainText_rejectsProfanity() {
        assertFalse(service.isSafePlainText("this is shit"));
    }

    // ---- isSafeRichText ----

    @Test
    void isSafeRichText_acceptsAllowedHtml() {
        assertTrue(service.isSafeRichText("<p>A <b>great</b> product.</p>"));
        assertTrue(service.isSafeRichText("multi\nline\nplain"));
    }

    @Test
    void isSafeRichText_rejectsScriptAndEventHandlers() {
        assertFalse(service.isSafeRichText("<script>bad()</script>"));
        assertFalse(service.isSafeRichText("<img src=x onerror=alert(1)>"));
        assertFalse(service.isSafeRichText("<a href=\"javascript:bad()\">x</a>"));
        assertFalse(service.isSafeRichText("<iframe src=\"x\"></iframe>"));
    }

    @Test
    void isSafeRichText_rejectsProfanityInBody() {
        assertFalse(service.isSafeRichText("<p>you are a bitch</p>"));
    }

    // ---- isSafeIdentifier ----

    @Test
    void isSafeIdentifier_acceptsCodeLike() {
        assertTrue(service.isSafeIdentifier("SKU-123"));
        assertTrue(service.isSafeIdentifier("sku.v2"));
        assertTrue(service.isSafeIdentifier("A_B-C.D"));
    }

    @Test
    void isSafeIdentifier_rejectsSpacesAndSymbols() {
        assertFalse(service.isSafeIdentifier("SKU 123"));
        assertFalse(service.isSafeIdentifier("SKU/123"));
        assertFalse(service.isSafeIdentifier("SKU<script>"));
    }

    // ---- DTO normalization ----

    @Test
    void normalize_CreateProductRequest_appliesAllFieldRules() {
        CreateProductRequest r = new CreateProductRequest();
        r.setName("  Widget   Pro  ");
        r.setDescription("  Line 1\nLine 2  ");
        r.setSku(" sku-1 ");
        r.setCurrency("usd");
        r.setCategory(" Electronics ");
        r.setBrand("  Acme  Corp  ");
        r.setTags(" tag1, tag2 ");
        r.setWeightUnit(" kg ");
        r.setPrice(new BigDecimal("10.00"));

        service.normalize(r);

        assertEquals("Widget Pro", r.getName());
        assertEquals("Line 1\nLine 2", r.getDescription());
        assertEquals("SKU-1", r.getSku());
        assertEquals("USD", r.getCurrency());
        assertEquals("electronics", r.getCategory());
        assertEquals("Acme Corp", r.getBrand());
        assertEquals("tag1, tag2", r.getTags());
        assertEquals("kg", r.getWeightUnit());
    }

    @Test
    void normalize_CreateRestockRequest_trimsSupplierNote() {
        CreateRestockRequest r = new CreateRestockRequest();
        r.setSupplierNote("  note  \nsecond line  ");
        service.normalize(r);
        assertEquals("note  \nsecond line", r.getSupplierNote());
    }

    @Test
    void normalize_AdjustStockRequest_trimsNote() {
        AdjustStockRequest r = new AdjustStockRequest();
        r.setNote("   spilled a box   ");
        service.normalize(r);
        assertEquals("spilled a box", r.getNote());
    }

    @Test
    void normalize_CreateLocationRequest_normalizesFields() {
        CreateLocationRequest r = new CreateLocationRequest();
        r.setName("  Main  Warehouse  ");
        r.setCode(" wh-01 ");
        r.setCity(" Sydney ");
        r.setCountry(" Australia ");
        r.setAddress("  1 Main St\nLevel 2  ");

        service.normalize(r);

        assertEquals("Main Warehouse", r.getName());
        assertEquals("WH-01", r.getCode());
        assertEquals("Sydney", r.getCity());
        assertEquals("Australia", r.getCountry());
        assertEquals("1 Main St\nLevel 2", r.getAddress());
    }

    @Test
    void normalize_SetProductAttributesRequest_normalizesEachItem() {
        SetProductAttributesRequest r = new SetProductAttributesRequest();
        List<SetProductAttributesRequest.AttributeItem> items = new ArrayList<>();
        SetProductAttributesRequest.AttributeItem item = new SetProductAttributesRequest.AttributeItem();
        item.setName("  Color  ");
        item.setValue(" Midnight  Blue ");
        items.add(item);
        r.setAttributes(items);

        service.normalize(r);

        assertEquals("Color", r.getAttributes().get(0).getName());
        assertEquals("Midnight Blue", r.getAttributes().get(0).getValue());
    }

    @Test
    void normalize_nullRequest_isNoop() {
        service.normalize((CreateProductRequest) null);
        service.normalize((CreateRestockRequest) null);
        service.normalize((CreateLocationRequest) null);
    }

    // ---- normalizeCode / normalizeCategory null pass-throughs ----

    @Test
    void normalizeCode_null_returnsNull() {
        assertNull(service.normalizeCode(null));
    }

    @Test
    void normalizeCategory_null_returnsNull() {
        assertNull(service.normalizeCategory(null));
    }

    @Test
    void normalizeText_emptyString_returnsEmpty() {
        assertEquals("", service.normalizeText(""));
    }

    // ---- isSafeRichText null/blank pass-throughs ----

    @Test
    void isSafeRichText_null_returnsTrue() {
        assertTrue(service.isSafeRichText(null));
    }

    @Test
    void isSafeRichText_blank_returnsTrue() {
        assertTrue(service.isSafeRichText("   "));
    }

    @Test
    void isSafeRichText_controlCharsNotLineBreaks_returnsFalse() {
        assertFalse(service.isSafeRichText("textcontrol"));
    }

    // ---- isSafeIdentifier null/blank ----

    @Test
    void isSafeIdentifier_null_returnsTrue() {
        assertTrue(service.isSafeIdentifier(null));
    }

    @Test
    void isSafeIdentifier_blank_returnsTrue() {
        assertTrue(service.isSafeIdentifier("   "));
    }

    // ---- containsProfanity ----

    @Test
    void containsProfanity_null_returnsFalse() {
        assertFalse(service.containsProfanity(null));
    }

    @Test
    void containsProfanity_blank_returnsFalse() {
        assertFalse(service.containsProfanity("  "));
    }

    @Test
    void containsProfanity_clean_returnsFalse() {
        assertFalse(service.containsProfanity("perfectly fine text"));
    }

    // ---- normalize overloads not yet covered ----

    @Test
    void normalize_UpdateProductRequest_normalizesAllFields() {
        UpdateProductRequest r = new UpdateProductRequest();
        r.setName("  Widget   ");
        r.setSku(" sku-2 ");
        r.setCurrency("eur");
        r.setCategory(" Furniture ");
        r.setBrand("  Ikea  ");
        r.setTags(" tag1 ");
        r.setWeightUnit(" lb ");

        service.normalize(r);

        assertEquals("Widget", r.getName());
        assertEquals("SKU-2", r.getSku());
        assertEquals("EUR", r.getCurrency());
        assertEquals("furniture", r.getCategory());
        assertEquals("Ikea", r.getBrand());
    }

    @Test
    void normalize_UpdateProductRequest_null_isNoop() {
        service.normalize((UpdateProductRequest) null);
    }

    @Test
    void normalize_BatchCreateProductsRequest_normalizesEach() {
        CreateProductRequest p1 = new CreateProductRequest();
        p1.setName("  Chair   ");
        BatchCreateProductsRequest r = new BatchCreateProductsRequest();
        r.setProducts(List.of(p1));

        service.normalize(r);

        assertEquals("Chair", r.getProducts().get(0).getName());
    }

    @Test
    void normalize_BatchCreateProductsRequest_nullProducts_isNoop() {
        BatchCreateProductsRequest r = new BatchCreateProductsRequest();
        r.setProducts(null);
        service.normalize(r);
    }

    @Test
    void normalize_BatchCreateProductsRequest_null_isNoop() {
        service.normalize((BatchCreateProductsRequest) null);
    }

    @Test
    void normalize_CreateProductVariantRequest_normalizesFields() {
        CreateProductVariantRequest r = new CreateProductVariantRequest();
        r.setSku(" var-sku ");
        r.setOption1("  Red  ");
        r.setOption2("  Large  ");
        r.setOption3("  Cotton  ");

        service.normalize(r);

        assertEquals("VAR-SKU", r.getSku());
        assertEquals("Red", r.getOption1());
        assertEquals("Large", r.getOption2());
        assertEquals("Cotton", r.getOption3());
    }

    @Test
    void normalize_CreateProductVariantRequest_null_isNoop() {
        service.normalize((CreateProductVariantRequest) null);
    }

    @Test
    void normalize_UpdateProductVariantRequest_normalizesFields() {
        UpdateProductVariantRequest r = new UpdateProductVariantRequest();
        r.setSku(" upd-sku ");
        r.setOption1("  Blue  ");

        service.normalize(r);

        assertEquals("UPD-SKU", r.getSku());
        assertEquals("Blue", r.getOption1());
    }

    @Test
    void normalize_UpdateProductVariantRequest_null_isNoop() {
        service.normalize((UpdateProductVariantRequest) null);
    }

    @Test
    void normalize_CreateProductOptionRequest_normalizesName() {
        CreateProductOptionRequest r = new CreateProductOptionRequest();
        r.setName("  Size  ");

        service.normalize(r);

        assertEquals("Size", r.getName());
    }

    @Test
    void normalize_CreateProductOptionRequest_null_isNoop() {
        service.normalize((CreateProductOptionRequest) null);
    }

    @Test
    void normalize_UpdateProductOptionRequest_normalizesName() {
        UpdateProductOptionRequest r = new UpdateProductOptionRequest();
        r.setName("  Colour  ");

        service.normalize(r);

        assertEquals("Colour", r.getName());
    }

    @Test
    void normalize_UpdateProductOptionRequest_null_isNoop() {
        service.normalize((UpdateProductOptionRequest) null);
    }

    @Test
    void normalize_AddProductImageRequest_normalizesUrl() {
        AddProductImageRequest r = new AddProductImageRequest();
        r.setImageUrl("  https://cdn/img.jpg  ");

        service.normalize(r);

        assertEquals("https://cdn/img.jpg", r.getImageUrl());
    }

    @Test
    void normalize_AddProductImageRequest_null_isNoop() {
        service.normalize((AddProductImageRequest) null);
    }

    @Test
    void normalize_UpdateRestockRequest_trimsNote() {
        UpdateRestockRequest r = new UpdateRestockRequest();
        r.setSupplierNote("  updated note  ");

        service.normalize(r);

        assertEquals("updated note", r.getSupplierNote());
    }

    @Test
    void normalize_UpdateRestockRequest_null_isNoop() {
        service.normalize((UpdateRestockRequest) null);
    }

    @Test
    void normalize_BulkAdjustRequest_normalizesEachItem() {
        BulkAdjustItem item = new BulkAdjustItem();
        item.setNote("  bulk note  ");
        BulkAdjustRequest r = new BulkAdjustRequest();
        r.setItems(List.of(item));

        service.normalize(r);

        assertEquals("bulk note", r.getItems().get(0).getNote());
    }

    @Test
    void normalize_BulkAdjustRequest_nullItems_isNoop() {
        BulkAdjustRequest r = new BulkAdjustRequest();
        r.setItems(null);
        service.normalize(r);
    }

    @Test
    void normalize_BulkAdjustRequest_null_isNoop() {
        service.normalize((BulkAdjustRequest) null);
    }

    @Test
    void normalize_UpdateLocationRequest_normalizesFields() {
        UpdateLocationRequest r = new UpdateLocationRequest();
        r.setName("  Depot  ");
        r.setCode(" dpt-2 ");
        r.setCity(" Melbourne ");
        r.setCountry(" Australia ");
        r.setAddress("  5 Dock St  ");

        service.normalize(r);

        assertEquals("Depot", r.getName());
        assertEquals("DPT-2", r.getCode());
        assertEquals("Melbourne", r.getCity());
        assertEquals("Australia", r.getCountry());
        assertEquals("5 Dock St", r.getAddress());
    }

    @Test
    void normalize_UpdateLocationRequest_null_isNoop() {
        service.normalize((UpdateLocationRequest) null);
    }
}
