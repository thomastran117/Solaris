package backend.services.impl;

import backend.dtos.requests.inventory.AdjustStockRequest;
import backend.dtos.requests.inventory.CreateLocationRequest;
import backend.dtos.requests.inventory.CreateRestockRequest;
import backend.dtos.requests.product.CreateProductRequest;
import backend.dtos.requests.product.SetProductAttributesRequest;
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
}
