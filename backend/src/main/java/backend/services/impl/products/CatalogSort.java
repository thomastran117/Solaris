package backend.services.impl.products;

import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.ScoreSort;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.FieldType;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

/**
 * Resolves the catalog {@code sort}/{@code direction} request parameters into an Elasticsearch
 * sort and the equivalent JPA sort used by the PostgreSQL paths.
 *
 * <p>Policy:
 * <ul>
 *   <li>{@code relevance} (and any unrecognised value) — {@code _score desc}, so the
 *       merchandising {@code function_score} (pins, boosts) drives the order; ties fall back to
 *       newest first. The JPA order is {@code createdAt desc}.</li>
 *   <li>{@code createdAt}, {@code price}, {@code stock}, {@code name} — that field in the requested
 *       direction, then {@code _score}.</li>
 * </ul>
 *
 * <p>Every ES field sort carries {@code unmapped_type} and {@code missing: _last}, so an index whose
 * documents predate a sort key (e.g. before {@code createdAt} was indexed) sorts those documents
 * last instead of failing the whole search with "No mapping found for [...] in order to sort on".
 */
public record CatalogSort(boolean relevance, List<SortOptions> esSorts, Sort jpaSort) {

    public static final String RELEVANCE = "relevance";
    public static final String DEFAULT_FIELD = "createdAt";

    /** Accepted field sorts → ES field name and the type used when the field is unmapped. */
    private static final Map<String, EsField> FIELDS = Map.of(
            "createdAt", new EsField("createdAt", FieldType.Date),
            "price",     new EsField("price",     FieldType.Double),
            "stock",     new EsField("stock",     FieldType.Integer),
            "name",      new EsField("name.sort", FieldType.Keyword));

    private record EsField(String path, FieldType unmappedType) {}

    public static boolean isFieldSort(String sort) {
        return sort != null && FIELDS.containsKey(sort);
    }

    public static CatalogSort resolve(String sort, String direction) {
        boolean asc = "asc".equalsIgnoreCase(direction);
        if (!isFieldSort(sort)) {
            return new CatalogSort(true,
                    List.of(scoreSort(), fieldSort(FIELDS.get(DEFAULT_FIELD), SortOrder.Desc)),
                    Sort.by(Sort.Direction.DESC, DEFAULT_FIELD));
        }
        SortOrder order = asc ? SortOrder.Asc : SortOrder.Desc;
        return new CatalogSort(false,
                List.of(fieldSort(FIELDS.get(sort), order), scoreSort()),
                Sort.by(asc ? Sort.Direction.ASC : Sort.Direction.DESC, sort));
    }

    private static SortOptions scoreSort() {
        return SortOptions.of(s -> s.score(ScoreSort.of(sc -> sc.order(SortOrder.Desc))));
    }

    private static SortOptions fieldSort(EsField field, SortOrder order) {
        return SortOptions.of(s -> s.field(FieldSort.of(f -> f
                .field(field.path())
                .order(order)
                .unmappedType(field.unmappedType())
                .missing("_last"))));
    }
}
