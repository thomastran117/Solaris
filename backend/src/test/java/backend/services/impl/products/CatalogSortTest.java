package backend.services.impl.products;

import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.FieldType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.*;

class CatalogSortTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"relevance", "", "unknownField", "created_at"})
    void resolve_relevanceOrUnknown_scoreFirstThenNewest(String sort) {
        CatalogSort result = CatalogSort.resolve(sort, "asc");

        assertTrue(result.relevance());
        assertEquals(2, result.esSorts().size());
        assertTrue(result.esSorts().get(0).isScore());
        assertEquals(SortOrder.Desc, result.esSorts().get(0).score().order());

        FieldSort tieBreak = result.esSorts().get(1).field();
        assertEquals("createdAt", tieBreak.field());
        assertEquals(SortOrder.Desc, tieBreak.order(), "relevance ignores direction; ties are newest first");
        assertEquals(FieldType.Date, tieBreak.unmappedType());

        assertEquals(Sort.Direction.DESC, result.jpaSort().getOrderFor("createdAt").getDirection());
    }

    @ParameterizedTest
    @CsvSource({
            "createdAt, createdAt, Date",
            "price,     price,     Double",
            "stock,     stock,     Integer",
            "name,      name.sort, Keyword"
    })
    void resolve_fieldSort_mapsToTolerantEsFieldThenScore(String sort, String esField, FieldType unmappedType) {
        CatalogSort result = CatalogSort.resolve(sort, "asc");

        assertFalse(result.relevance());
        assertEquals(2, result.esSorts().size());

        FieldSort primary = result.esSorts().get(0).field();
        assertEquals(esField, primary.field());
        assertEquals(SortOrder.Asc, primary.order());
        assertEquals(unmappedType, primary.unmappedType(), "unmapped_type keeps old indices from failing the query");
        assertEquals("_last", primary.missing().stringValue());
        assertTrue(result.esSorts().get(1).isScore());

        assertEquals(Sort.Direction.ASC, result.jpaSort().getOrderFor(sort).getDirection());
    }

    @Test
    void resolve_fieldSort_defaultsToDescendingForAnyNonAscDirection() {
        assertEquals(SortOrder.Desc, CatalogSort.resolve("price", "desc").esSorts().get(0).field().order());
        assertEquals(SortOrder.Desc, CatalogSort.resolve("price", null).esSorts().get(0).field().order());
        assertEquals(SortOrder.Asc, CatalogSort.resolve("price", "ASC").esSorts().get(0).field().order());
        assertEquals(Sort.Direction.DESC, CatalogSort.resolve("price", null).jpaSort().getOrderFor("price").getDirection());
    }

    @Test
    void isFieldSort_onlyAcceptsMappedFields() {
        assertTrue(CatalogSort.isFieldSort("createdAt"));
        assertTrue(CatalogSort.isFieldSort("stock"));
        assertFalse(CatalogSort.isFieldSort("relevance"));
        assertFalse(CatalogSort.isFieldSort(null));
        assertFalse(CatalogSort.isFieldSort("name.sort"));
    }
}
