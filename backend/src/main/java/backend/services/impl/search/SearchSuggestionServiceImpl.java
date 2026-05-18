package backend.services.impl.search;

import backend.documents.ProductDocument;
import backend.dtos.responses.search.SearchSuggestionsResponse;
import backend.services.impl.SingleFlightCache;
import backend.services.intf.search.SearchSuggestionService;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SearchSuggestionServiceImpl implements SearchSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SearchSuggestionServiceImpl.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final SingleFlightCache singleFlightCache;

    @Value("${app.product.cache-ttl-short-seconds:60}")
    private long cacheTtlShort;

    public SearchSuggestionServiceImpl(ElasticsearchOperations elasticsearchOperations,
                                        SingleFlightCache singleFlightCache) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.singleFlightCache = singleFlightCache;
    }

    @Override
    public SearchSuggestionsResponse getSuggestions(UUID marketplaceId, String q, int limit) {
        if (q == null || q.isBlank() || q.length() < 2) {
            return new SearchSuggestionsResponse(List.of(), List.of(), List.of());
        }
        int clampedLimit = Math.min(limit, 10);
        String cacheKey = String.format("suggestions:%s:%s:%d", marketplaceId, q.toLowerCase(), clampedLimit);

        return singleFlightCache.getOrLoad(cacheKey, cacheTtlShort, () -> {
            try {
                final String marketplaceIdStr = marketplaceId.toString();
                BoolQuery.Builder bq = new BoolQuery.Builder()
                        .filter(TermQuery.of(t -> t.field("marketplaceId").value(marketplaceIdStr))._toQuery())
                        .filter(TermQuery.of(t -> t.field("marketplaceListed").value(true))._toQuery())
                        .filter(TermQuery.of(t -> t.field("status").value("ACTIVE"))._toQuery())
                        .must(co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery.of(m -> m
                                .field("nameCompletion")
                                .query(q))._toQuery());

                NativeQuery query = NativeQuery.builder()
                        .withQuery(bq.build()._toQuery())
                        .withPageable(PageRequest.of(0, clampedLimit))
                        .withAggregation("categories",
                                Aggregation.of(a -> a.terms(t -> t.field("category").size(5))))
                        .withAggregation("brands",
                                Aggregation.of(a -> a.terms(t -> t.field("brand").size(5))))
                        .build();

                SearchHits<ProductDocument> hits = elasticsearchOperations.search(query, ProductDocument.class);

                List<String> productNames = hits.stream()
                        .map(h -> h.getContent().getName())
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .limit(clampedLimit)
                        .toList();

                List<String> categories = List.of();
                List<String> brands = List.of();

                if (hits.getAggregations() != null) {
                    ElasticsearchAggregations aggs = (ElasticsearchAggregations) hits.getAggregations();
                    Map<String, ElasticsearchAggregation> aggMap = aggs.aggregationsAsMap();

                    if (aggMap.containsKey("categories")) {
                        categories = aggMap.get("categories").aggregation().getAggregate()
                                .sterms().buckets().array().stream()
                                .map(b -> b.key().stringValue())
                                .filter(k -> k != null && !k.isBlank())
                                .toList();
                    }
                    if (aggMap.containsKey("brands")) {
                        brands = aggMap.get("brands").aggregation().getAggregate()
                                .sterms().buckets().array().stream()
                                .map(b -> b.key().stringValue())
                                .filter(k -> k != null && !k.isBlank())
                                .toList();
                    }
                }

                return new SearchSuggestionsResponse(productNames, categories, brands);

            } catch (Exception e) {
                log.warn("[SUGGESTIONS] Elasticsearch unavailable: {}", e.getMessage());
                return new SearchSuggestionsResponse(List.of(), List.of(), List.of());
            }
        }, new TypeReference<SearchSuggestionsResponse>() {});
    }

    @Override
    public SearchSuggestionsResponse getCompanySuggestions(UUID companyId, String q, int limit) {
        if (q == null || q.isBlank() || q.length() < 2) {
            return new SearchSuggestionsResponse(List.of(), List.of(), List.of());
        }
        int clampedLimit = Math.min(limit, 10);
        String cacheKey = String.format("suggestions:company:%s:%s:%d", companyId, q.toLowerCase(), clampedLimit);

        return singleFlightCache.getOrLoad(cacheKey, cacheTtlShort, () -> {
            try {
                final String companyIdStr = companyId.toString();
                BoolQuery.Builder bq = new BoolQuery.Builder()
                        .filter(TermQuery.of(t -> t.field("companyId").value(companyIdStr))._toQuery())
                        .filter(TermQuery.of(t -> t.field("status").value("ACTIVE"))._toQuery())
                        .must(co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery.of(m -> m
                                .field("nameCompletion")
                                .query(q))._toQuery());

                NativeQuery query = NativeQuery.builder()
                        .withQuery(bq.build()._toQuery())
                        .withPageable(PageRequest.of(0, clampedLimit))
                        .withAggregation("categories",
                                Aggregation.of(a -> a.terms(t -> t.field("category").size(5))))
                        .withAggregation("brands",
                                Aggregation.of(a -> a.terms(t -> t.field("brand").size(5))))
                        .build();

                SearchHits<ProductDocument> hits = elasticsearchOperations.search(query, ProductDocument.class);

                List<String> productNames = hits.stream()
                        .map(h -> h.getContent().getName())
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .limit(clampedLimit)
                        .toList();

                List<String> categories = List.of();
                List<String> brands = List.of();

                if (hits.getAggregations() != null) {
                    ElasticsearchAggregations aggs = (ElasticsearchAggregations) hits.getAggregations();
                    Map<String, ElasticsearchAggregation> aggMap = aggs.aggregationsAsMap();

                    if (aggMap.containsKey("categories")) {
                        categories = aggMap.get("categories").aggregation().getAggregate()
                                .sterms().buckets().array().stream()
                                .map(b -> b.key().stringValue())
                                .filter(k -> k != null && !k.isBlank())
                                .toList();
                    }
                    if (aggMap.containsKey("brands")) {
                        brands = aggMap.get("brands").aggregation().getAggregate()
                                .sterms().buckets().array().stream()
                                .map(b -> b.key().stringValue())
                                .filter(k -> k != null && !k.isBlank())
                                .toList();
                    }
                }

                return new SearchSuggestionsResponse(productNames, categories, brands);

            } catch (Exception e) {
                log.warn("[SUGGESTIONS] Elasticsearch unavailable: {}", e.getMessage());
                return new SearchSuggestionsResponse(List.of(), List.of(), List.of());
            }
        }, new TypeReference<SearchSuggestionsResponse>() {});
    }
}
