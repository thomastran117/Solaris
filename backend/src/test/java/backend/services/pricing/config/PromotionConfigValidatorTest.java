package backend.services.pricing.config;

import backend.exceptions.http.BadRequestException;
import backend.models.enums.PromotionRuleType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromotionConfigValidatorTest {

    private PromotionConfigValidator validator;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        validator = new PromotionConfigValidator(mapper);
    }

    private JsonNode json(String json) throws Exception {
        return mapper.readTree(json);
    }

    // ── null config ───────────────────────────────────────────────────────────

    @Test
    void validateAndSerialise_nullConfig_throws() throws Exception {
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.PERCENTAGE_OFF, json("null")));
    }

    // ── PERCENTAGE_OFF ────────────────────────────────────────────────────────

    @Test
    void percentageOff_valid_serialises() throws Exception {
        JsonNode config = json("{\"percent\":10,\"appliesTo\":\"LINE\"}");
        String result = validator.validateAndSerialise(PromotionRuleType.PERCENTAGE_OFF, config);
        assertNotNull(result);
        assertTrue(result.contains("percent"));
    }

    @Test
    void percentageOff_percentZero_throws() throws Exception {
        JsonNode config = json("{\"percent\":0,\"appliesTo\":\"LINE\"}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.PERCENTAGE_OFF, config));
    }

    @Test
    void percentageOff_percentNegative_throws() throws Exception {
        JsonNode config = json("{\"percent\":-5,\"appliesTo\":\"LINE\"}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.PERCENTAGE_OFF, config));
    }

    @Test
    void percentageOff_percentOver100_throws() throws Exception {
        JsonNode config = json("{\"percent\":101,\"appliesTo\":\"LINE\"}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.PERCENTAGE_OFF, config));
    }

    @Test
    void percentageOff_nullAppliesTo_throws() throws Exception {
        JsonNode config = json("{\"percent\":10}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.PERCENTAGE_OFF, config));
    }

    @Test
    void percentageOff_maxDiscountZero_throws() throws Exception {
        JsonNode config = json("{\"percent\":10,\"maxDiscount\":0,\"appliesTo\":\"ORDER\"}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.PERCENTAGE_OFF, config));
    }

    @Test
    void percentageOff_maxDiscount100_valid() throws Exception {
        JsonNode config = json("{\"percent\":10,\"maxDiscount\":100,\"appliesTo\":\"ORDER\"}");
        assertNotNull(validator.validateAndSerialise(PromotionRuleType.PERCENTAGE_OFF, config));
    }

    // ── FIXED_OFF ─────────────────────────────────────────────────────────────

    @Test
    void fixedOff_valid_serialises() throws Exception {
        JsonNode config = json("{\"amount\":5.00,\"appliesTo\":\"ORDER\"}");
        String result = validator.validateAndSerialise(PromotionRuleType.FIXED_OFF, config);
        assertNotNull(result);
    }

    @Test
    void fixedOff_amountZero_throws() throws Exception {
        JsonNode config = json("{\"amount\":0,\"appliesTo\":\"ORDER\"}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.FIXED_OFF, config));
    }

    @Test
    void fixedOff_amountNegative_throws() throws Exception {
        JsonNode config = json("{\"amount\":-1,\"appliesTo\":\"ORDER\"}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.FIXED_OFF, config));
    }

    @Test
    void fixedOff_nullAppliesTo_throws() throws Exception {
        JsonNode config = json("{\"amount\":5.00}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.FIXED_OFF, config));
    }

    // ── BOGO ──────────────────────────────────────────────────────────────────

    @Test
    void bogo_valid_serialises() throws Exception {
        JsonNode config = json("{\"triggerQty\":2,\"rewardQty\":1,\"rewardPercentOff\":100," +
                "\"maxApplicationsPerOrder\":3}");
        String result = validator.validateAndSerialise(PromotionRuleType.BOGO, config);
        assertNotNull(result);
    }

    @Test
    void bogo_triggerQtyZero_throws() throws Exception {
        JsonNode config = json("{\"triggerQty\":0,\"rewardQty\":1,\"rewardPercentOff\":100," +
                "\"maxApplicationsPerOrder\":1}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.BOGO, config));
    }

    @Test
    void bogo_rewardQtyZero_throws() throws Exception {
        JsonNode config = json("{\"triggerQty\":1,\"rewardQty\":0,\"rewardPercentOff\":100," +
                "\"maxApplicationsPerOrder\":1}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.BOGO, config));
    }

    @Test
    void bogo_maxApplicationsZero_throws() throws Exception {
        JsonNode config = json("{\"triggerQty\":1,\"rewardQty\":1,\"rewardPercentOff\":50," +
                "\"maxApplicationsPerOrder\":0}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.BOGO, config));
    }

    @Test
    void bogo_rewardPercentOver100_throws() throws Exception {
        JsonNode config = json("{\"triggerQty\":1,\"rewardQty\":1,\"rewardPercentOff\":101," +
                "\"maxApplicationsPerOrder\":1}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.BOGO, config));
    }

    @Test
    void bogo_rewardPercentNegative_throws() throws Exception {
        JsonNode config = json("{\"triggerQty\":1,\"rewardQty\":1,\"rewardPercentOff\":-1," +
                "\"maxApplicationsPerOrder\":1}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.BOGO, config));
    }

    // ── TIERED_PRICE ──────────────────────────────────────────────────────────

    @Test
    void tieredPrice_valid_serialises() throws Exception {
        JsonNode config = json("{\"breakpoints\":[{\"minQty\":1,\"unitPrice\":10},{\"minQty\":5,\"unitPrice\":8}]}");
        assertNotNull(validator.validateAndSerialise(PromotionRuleType.TIERED_PRICE, config));
    }

    @Test
    void tieredPrice_emptyBreakpoints_throws() throws Exception {
        JsonNode config = json("{\"breakpoints\":[]}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.TIERED_PRICE, config));
    }

    @Test
    void tieredPrice_nullBreakpoints_throws() throws Exception {
        JsonNode config = json("{}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.TIERED_PRICE, config));
    }

    @Test
    void tieredPrice_minQtyZero_throws() throws Exception {
        JsonNode config = json("{\"breakpoints\":[{\"minQty\":0,\"unitPrice\":10}]}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.TIERED_PRICE, config));
    }

    @Test
    void tieredPrice_duplicateMinQty_throws() throws Exception {
        JsonNode config = json("{\"breakpoints\":[{\"minQty\":5,\"unitPrice\":10},{\"minQty\":5,\"unitPrice\":8}]}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.TIERED_PRICE, config));
    }

    @Test
    void tieredPrice_descendingMinQty_throws() throws Exception {
        JsonNode config = json("{\"breakpoints\":[{\"minQty\":5,\"unitPrice\":8},{\"minQty\":2,\"unitPrice\":10}]}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.TIERED_PRICE, config));
    }

    // ── FREE_SHIPPING ─────────────────────────────────────────────────────────

    @Test
    void freeShipping_noMaxDiscount_valid() throws Exception {
        JsonNode config = json("{}");
        assertNotNull(validator.validateAndSerialise(PromotionRuleType.FREE_SHIPPING, config));
    }

    @Test
    void freeShipping_positiveMaxDiscount_valid() throws Exception {
        JsonNode config = json("{\"maxShippingDiscount\":10}");
        assertNotNull(validator.validateAndSerialise(PromotionRuleType.FREE_SHIPPING, config));
    }

    @Test
    void freeShipping_zeroMaxDiscount_throws() throws Exception {
        JsonNode config = json("{\"maxShippingDiscount\":0}");
        assertThrows(BadRequestException.class, () ->
                validator.validateAndSerialise(PromotionRuleType.FREE_SHIPPING, config));
    }

    // ── parseStored ───────────────────────────────────────────────────────────

    @Test
    void parseStored_validJson_returnsTypedConfig() {
        String json = "{\"percent\":25,\"appliesTo\":\"LINE\"}";
        Object result = validator.parseStored(PromotionRuleType.PERCENTAGE_OFF, json);
        assertInstanceOf(PercentageOffConfig.class, result);
        assertEquals(new java.math.BigDecimal("25"), ((PercentageOffConfig) result).percent());
    }

    @Test
    void parseStored_corruptJson_throwsIllegalState() {
        assertThrows(IllegalStateException.class, () ->
                validator.parseStored(PromotionRuleType.PERCENTAGE_OFF, "not-json"));
    }
}
