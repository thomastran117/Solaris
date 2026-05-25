package backend.services.impl;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import backend.documents.ReportDocument;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.report.ReportResponse;
import backend.models.enums.ReportReason;
import backend.models.enums.ReportStatus;
import backend.models.enums.ReportTargetType;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.UserRepository;
import backend.services.intf.ReportSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReportSearchServiceImpl implements ReportSearchService {

    private static final Logger log = LoggerFactory.getLogger(ReportSearchServiceImpl.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final ProductReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public ReportSearchServiceImpl(
            ElasticsearchOperations elasticsearchOperations,
            ProductRepository productRepository,
            CompanyRepository companyRepository,
            ProductReviewRepository reviewRepository,
            UserRepository userRepository) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.productRepository = productRepository;
        this.companyRepository = companyRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    @Override
    public PagedResponse<ReportResponse> search(
            String q,
            ReportTargetType targetType,
            ReportStatus status,
            ReportReason reason,
            Instant createdAfter,
            Instant createdBefore,
            String sort,
            int page,
            int size) {

        int clampedSize = Math.min(Math.max(1, size), 50);
        int clampedPage = Math.max(0, page);
        String trimmedQ = q == null ? "" : q.trim();

        BoolQuery.Builder bq = new BoolQuery.Builder();

        if (status != null) {
            bq.filter(TermQuery.of(t -> t.field("status").value(status.name()))._toQuery());
        }
        if (targetType != null) {
            bq.filter(TermQuery.of(t -> t.field("targetType").value(targetType.name()))._toQuery());
        }
        if (reason != null) {
            bq.filter(TermQuery.of(t -> t.field("reason").value(reason.name()))._toQuery());
        }
        if (createdAfter != null || createdBefore != null) {
            final Instant after = createdAfter;
            final Instant before = createdBefore;
            bq.filter(RangeQuery.of(r -> {
                var d = r.date(rb -> {
                    rb.field("createdAt");
                    if (after != null) rb.gte(after.toString());
                    if (before != null) rb.lte(before.toString());
                    return rb;
                });
                return d;
            })._toQuery());
        }
        if (!trimmedQ.isEmpty()) {
            final String fq = trimmedQ;
            bq.must(MultiMatchQuery.of(m -> m
                    .query(fq)
                    .fields("title^2", "description", "targetName", "reporterName")
                    .fuzziness("AUTO"))._toQuery());
        }

        Query query = bq.build()._toQuery();

        List<SortOptions> sortOptions = buildSort(sort);

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(PageRequest.of(clampedPage, clampedSize))
                .withSort(sortOptions)
                .build();

        try {
            SearchHits<ReportDocument> hits = elasticsearchOperations.search(nativeQuery, ReportDocument.class);
            List<ReportResponse> results = new ArrayList<>();
            for (SearchHit<ReportDocument> hit : hits.getSearchHits()) {
                ReportDocument doc = hit.getContent();
                String targetName = resolveTargetName(doc);
                String reporterName = resolveReporterName(doc.getReporterId());
                results.add(toResponse(doc, targetName, reporterName));
            }
            long total = hits.getTotalHits();
            var springPage = new PageImpl<>(results, PageRequest.of(clampedPage, clampedSize), total);
            return new PagedResponse<>(springPage);
        } catch (Exception e) {
            log.warn("[REPORT SEARCH] ES query failed, returning empty: {}", e.getMessage());
            var empty = new PageImpl<ReportResponse>(List.of(), PageRequest.of(clampedPage, clampedSize), 0);
            return new PagedResponse<>(empty);
        }
    }

    private List<SortOptions> buildSort(String sort) {
        if ("oldest".equals(sort)) {
            return List.of(SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Asc))));
        }
        if ("status".equals(sort)) {
            return List.of(
                    SortOptions.of(s -> s.field(f -> f.field("status").order(SortOrder.Asc))),
                    SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)))
            );
        }
        // default: newest
        return List.of(SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc))));
    }

    private String resolveTargetName(ReportDocument doc) {
        if (doc.getTargetName() != null && !doc.getTargetName().isBlank()) {
            return doc.getTargetName();
        }
        if (doc.getTargetType() == null || doc.getTargetId() == null) return null;
        try {
            UUID targetId = UUID.fromString(doc.getTargetId());
            return switch (doc.getTargetType()) {
                case "PRODUCT" -> productRepository.findById(targetId).map(p -> p.getName()).orElse("Deleted product");
                case "COMPANY" -> companyRepository.findById(targetId).map(c -> c.getName()).orElse("Deleted company");
                case "REVIEW" -> reviewRepository.findById(targetId)
                        .map(r -> "Review by " + displayName(r.getReviewer() != null ? r.getReviewer().getFirstName() : null,
                                r.getReviewer() != null ? r.getReviewer().getLastName() : null))
                        .orElse("Deleted review");
                case "USER" -> userRepository.findById(targetId)
                        .map(u -> "User: " + displayName(u.getFirstName(), u.getLastName()))
                        .orElse("Deleted user");
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveReporterName(String reporterIdStr) {
        if (reporterIdStr == null) return null;
        try {
            UUID reporterId = UUID.fromString(reporterIdStr);
            return userRepository.findById(reporterId)
                    .map(u -> displayName(u.getFirstName(), u.getLastName()))
                    .orElse("Unknown");
        } catch (Exception e) {
            return null;
        }
    }

    private String displayName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName;
        String last = lastName == null ? "" : lastName;
        return (first + " " + last).trim();
    }

    private ReportResponse toResponse(ReportDocument doc, String targetName, String reporterName) {
        // Build a minimal Report-like response from the ES document.
        // We map fields directly to avoid a DB round-trip for list views.
        return new ReportResponse(
                toReportShell(doc),
                targetName,
                reporterName
        );
    }

    private backend.models.core.Report toReportShell(ReportDocument doc) {
        backend.models.core.Report r = new backend.models.core.Report();
        r.setId(doc.getId());
        r.setTargetType(doc.getTargetType() != null
                ? ReportTargetType.valueOf(doc.getTargetType()) : null);
        r.setTargetId(doc.getTargetId() != null ? UUID.fromString(doc.getTargetId()) : null);
        r.setReporterId(doc.getReporterId() != null ? UUID.fromString(doc.getReporterId()) : null);
        r.setReason(doc.getReason() != null ? ReportReason.valueOf(doc.getReason()) : null);
        r.setTitle(doc.getTitle());
        r.setDescription(doc.getDescription());
        r.setStatus(doc.getStatus() != null ? ReportStatus.valueOf(doc.getStatus()) : null);
        r.setCreatedAt(doc.getCreatedAt());
        r.setResolvedAt(doc.getResolvedAt());
        return r;
    }
}
