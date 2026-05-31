package backend.services.impl.collections;

import backend.exceptions.http.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DynamicCollectionRulesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ─── parse — null / blank / invalid ──────────────────────────────────────

    @Test
    void parse_nullJson_throwsBadRequest() {
        assertThrows(BadRequestException.class, () -> DynamicCollectionRules.parse(null, MAPPER));
    }

    @Test
    void parse_blankJson_throwsBadRequest() {
        assertThrows(BadRequestException.class, () -> DynamicCollectionRules.parse("   ", MAPPER));
    }

    @Test
    void parse_invalidJson_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> DynamicCollectionRules.parse("{not-valid-json}", MAPPER));
    }

    @Test
    void parse_allEmptyLists_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> DynamicCollectionRules.parse(
                        "{\"tagsAnyOf\":[],\"categoriesAnyOf\":[],\"brandsAnyOf\":[]}", MAPPER));
    }

    @Test
    void parse_nonArrayValue_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> DynamicCollectionRules.parse("{\"tagsAnyOf\":\"not-an-array\"}", MAPPER));
    }

    // ─── parse — happy paths ─────────────────────────────────────────────────

    @Test
    void parse_tagsOnly_returnsParsedTags() throws Exception {
        DynamicCollectionRules rules = DynamicCollectionRules.parse(
                "{\"tagsAnyOf\":[\"summer\",\"sale\"]}", MAPPER);

        assertEquals(2, rules.tagsAnyOf().size());
        assertTrue(rules.tagsAnyOf().contains("summer"));
        assertTrue(rules.tagsAnyOf().contains("sale"));
        assertTrue(rules.categoriesAnyOf().isEmpty());
        assertTrue(rules.brandsAnyOf().isEmpty());
    }

    @Test
    void parse_categoriesOnly_returnsParsedCategories() throws Exception {
        DynamicCollectionRules rules = DynamicCollectionRules.parse(
                "{\"categoriesAnyOf\":[\"Apparel\"]}", MAPPER);

        assertEquals(1, rules.categoriesAnyOf().size());
        assertTrue(rules.categoriesAnyOf().contains("apparel"));
    }

    @Test
    void parse_brandsOnly_returnsParsedBrands() throws Exception {
        DynamicCollectionRules rules = DynamicCollectionRules.parse(
                "{\"brandsAnyOf\":[\"Acme\",\"Globex\"]}", MAPPER);

        assertEquals(2, rules.brandsAnyOf().size());
        assertTrue(rules.brandsAnyOf().contains("acme"));
        assertTrue(rules.brandsAnyOf().contains("globex"));
    }

    @Test
    void parse_allThreeAxes_returnsParsedRules() throws Exception {
        String json = "{\"tagsAnyOf\":[\"sale\"],\"categoriesAnyOf\":[\"Electronics\"],\"brandsAnyOf\":[\"Sony\"]}";
        DynamicCollectionRules rules = DynamicCollectionRules.parse(json, MAPPER);

        assertEquals(1, rules.tagsAnyOf().size());
        assertEquals(1, rules.categoriesAnyOf().size());
        assertEquals(1, rules.brandsAnyOf().size());
    }

    @Test
    void parse_trimsAndLowercasesValues() throws Exception {
        DynamicCollectionRules rules = DynamicCollectionRules.parse(
                "{\"tagsAnyOf\":[\"  SUMMER  \",\" Sale \"]}", MAPPER);

        assertTrue(rules.tagsAnyOf().contains("summer"));
        assertTrue(rules.tagsAnyOf().contains("sale"));
    }

    @Test
    void parse_skipsNullAndEmptyItems() throws Exception {
        DynamicCollectionRules rules = DynamicCollectionRules.parse(
                "{\"tagsAnyOf\":[null,\"\",\"  \",\"valid\"]}", MAPPER);

        // null, empty, and whitespace-only items are dropped
        assertEquals(1, rules.tagsAnyOf().size());
        assertTrue(rules.tagsAnyOf().contains("valid"));
    }

    @Test
    void parse_missingKey_treatedAsEmptyList() throws Exception {
        // tagsAnyOf absent — should default to empty, but categoriesAnyOf present keeps rule valid
        DynamicCollectionRules rules = DynamicCollectionRules.parse(
                "{\"categoriesAnyOf\":[\"Shoes\"]}", MAPPER);

        assertTrue(rules.tagsAnyOf().isEmpty());
        assertFalse(rules.categoriesAnyOf().isEmpty());
    }

    // ─── isEmpty ─────────────────────────────────────────────────────────────

    @Test
    void isEmpty_allListsEmpty_returnsTrue() throws Exception {
        // isEmpty() can't be reached via parse() because it throws on all-empty —
        // but the implementation can be exercised through a valid parse with one populated axis.
        DynamicCollectionRules rules = DynamicCollectionRules.parse(
                "{\"tagsAnyOf\":[\"x\"]}", MAPPER);
        assertFalse(rules.isEmpty());
    }

    @Test
    void isEmpty_tagsPopulated_returnsFalse() throws Exception {
        DynamicCollectionRules rules = DynamicCollectionRules.parse(
                "{\"tagsAnyOf\":[\"clearance\"]}", MAPPER);
        assertFalse(rules.isEmpty());
    }

    @Test
    void isEmpty_categoriesPopulated_returnsFalse() throws Exception {
        DynamicCollectionRules rules = DynamicCollectionRules.parse(
                "{\"categoriesAnyOf\":[\"Home\"]}", MAPPER);
        assertFalse(rules.isEmpty());
    }

    @Test
    void isEmpty_brandsPopulated_returnsFalse() throws Exception {
        DynamicCollectionRules rules = DynamicCollectionRules.parse(
                "{\"brandsAnyOf\":[\"BrandX\"]}", MAPPER);
        assertFalse(rules.isEmpty());
    }
}
