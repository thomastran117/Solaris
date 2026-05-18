package backend.services.impl.products;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import backend.documents.ReviewDocument;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.review.ReviewSearchHit;
import backend.models.enums.ReviewStatus;
import backend.services.intf.products.ReviewSearchService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReviewSearchServiceImpl implements ReviewSearchService {

    private static final Logger log = LoggerFactory.getLogger(ReviewSearchServiceImpl.class);
    private static final Set<String> SORTABLE = Set.of("relevance", "createdAt", "helpfulCount");

    private final ElasticsearchOperations elasticsearchOperations;

    public ReviewSearchServiceImpl(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @Override
    public PagedResponse<ReviewSearchHit> search(UUID companyId, UUID productId, String q,
                                                  List<Integer> ratings, Boolean verifiedOnly,
                                                  String sort, int page, int size) {
        int clampedSize = Math.min(Math.max(1, size), 50);
        int clampedPage = Math.max(0, page);
        String effectiveSort = (sort != null && SORTABLE.contains(sort)) ? sort : "relevance";
        String trimmedQ = q == null ? "" : q.trim();

        BoolQuery.Builder bq = new BoolQuery.Builder()
                .filter(TermQuery.of(t -> t.field("productId").value(productId))._toQuery())
                .filter(TermQuery.of(t -> t.field("companyId").value(companyId))._toQuery())
                .filter(TermQuery.of(t -> t.field("status").value(ReviewStatus.PUBLISHED.name()))._toQuery());

        if (!trimmedQ.isEmpty()) {
            bq.must(MultiMatchQuery.of(m -> m
                    .query(trimmedQ)
                    .fields("title^2", "body")
                    .fuzziness("AUTO"))._toQuery());
        }

        if (ratings != null && !ratings.isEmpty()) {
            List<FieldValue> values = ratings.stream()
                    .filter(r -> r >= 1 && r <= 5)
                    .map(FieldValue::of)
                    .toList();
            if (!values.isEmpty()) {
                bq.filter(TermsQuery.of(t -> t
                        .field("rating")
                        .terms(TermsQueryField.of(tf -> tf.value(values))))._toQuery());
            }
        }

        if (Boolean.TRUE.equals(verifiedOnly)) {
            bq.filter(TermQuery.of(t -> t.field("verifiedPurchase").value(true))._toQuery());
        }

        Query query = bq.build()._toQuery();

        var queryBuilder = NativeQuery.builder()
                .withQuery(query)
                .withPageable(PageRequest.of(clampedPage, clampedSize))
                .withHighlightQuery(buildHighlight());

        if (!"relevance".equals(effectiveSort)) {
            queryBuilder.withSort(SortOptions.of(s -> s.field(f -> f.field(effectiveSort).order(SortOrder.Desc))));
        }

        SearchHits<ReviewDocument> hits;
        try {
            hits = elasticsearchOperations.search(queryBuilder.build(), ReviewDocument.class);
        } catch (Exception e) {
            log.warn("[REVIEW SEARCH] Query failed: {}", e.getMessage());
            return new PagedResponse<>(new PageImpl<ReviewSearchHit>(
                    List.of(), PageRequest.of(clampedPage, clampedSize), 0));
        }

        List<ReviewSearchHit> content = new ArrayList<>();
        for (SearchHit<ReviewDocument> h : hits) {
            ReviewDocument d = h.getContent();
            Map<String, List<String>> highlightMap = h.getHighlightFields();
            List<String> titleHi = highlightMap.getOrDefault("title", Collections.emptyList());
            List<String> bodyHi = highlightMap.getOrDefault("body", Collections.emptyList());
            float score = h.getScore() == null ? Float.NaN : h.getScore();
            content.add(new ReviewSearchHit(
                    d.getId(),
                    d.getProductId(),
                    d.getReviewerId(),
                    d.getReviewerName(),
                    d.getRating(),
                    d.getTitle(),
                    d.getBody(),
                    d.isVerifiedPurchase(),
                    d.isHasMedia(),
                    d.getHelpfulCount(),
                    d.getStatus(),
                    d.getCreatedAt(),
                    d.getUpdatedAt(),
                    titleHi,
                    bodyHi,
                    Double.isNaN(score) ? 0d : score));
        }

        PageImpl<ReviewSearchHit> pageImpl = new PageImpl<>(
                content, PageRequest.of(clampedPage, clampedSize), hits.getTotalHits());
        return new PagedResponse<>(pageImpl);
    }

    private HighlightQuery buildHighlight() {
        HighlightFieldParameters titleParams = HighlightFieldParameters.builder()
                .withNumberOfFragments(0)
                .build();
        HighlightFieldParameters bodyParams = HighlightFieldParameters.builder()
                .withNumberOfFragments(3)
                .withFragmentSize(160)
                .build();
        HighlightParameters params = HighlightParameters.builder()
                .withPreTags("<mark>")
                .withPostTags("</mark>")
                .build();
        Highlight highlight = new Highlight(
                params,
                List.of(new HighlightField("title", titleParams),
                        new HighlightField("body", bodyParams)));
        return new HighlightQuery(highlight, ReviewDocument.class);
    }
}
