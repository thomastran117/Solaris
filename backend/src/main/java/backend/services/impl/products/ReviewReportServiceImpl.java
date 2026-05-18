package backend.services.impl.products;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import backend.dtos.requests.review.ModerateReviewRequest;
import backend.dtos.requests.review.ReportReviewRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.review.ReviewMediaResponse;
import backend.dtos.responses.review.ReviewReportResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.ProductReview;
import backend.models.core.ReviewMedia;
import backend.models.core.ReviewReport;
import backend.models.core.User;
import backend.models.enums.ModerationAction;
import backend.models.enums.ReportStatus;
import backend.models.enums.ReviewStatus;
import backend.kafka.workers.ReviewIndexingService;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReviewMediaRepository;
import backend.repositories.ReviewReportRepository;
import backend.services.impl.SingleFlightCache;
import backend.services.intf.products.ReviewReportService;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewReportServiceImpl implements ReviewReportService {

    private final ProductReviewRepository reviewRepository;
    private final ReviewReportRepository reportRepository;
    private final ReviewMediaRepository mediaRepository;
    private final ReviewIndexingService reviewIndexingService;
    private final SingleFlightCache singleFlightCache;
    private final int autoHideThreshold;

    public ReviewReportServiceImpl(
            ProductReviewRepository reviewRepository,
            ReviewReportRepository reportRepository,
            ReviewMediaRepository mediaRepository,
            ReviewIndexingService reviewIndexingService,
            SingleFlightCache singleFlightCache,
            @Value("${app.review.auto-hide-threshold:5}") int autoHideThreshold) {
        this.reviewRepository = reviewRepository;
        this.reportRepository = reportRepository;
        this.mediaRepository = mediaRepository;
        this.reviewIndexingService = reviewIndexingService;
        this.singleFlightCache = singleFlightCache;
        this.autoHideThreshold = autoHideThreshold;
    }

    @Override
    @Transactional
    public void reportReview(UUID companyId, UUID productId, UUID reviewId, UUID reporterId, ReportReviewRequest request) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        if (review.getProduct() == null
                || !review.getProduct().getId().equals(productId)
                || review.getProduct().getCompany() == null
                || !review.getProduct().getCompany().getId().equals(companyId)) {
            throw new ResourceNotFoundException("Review not found");
        }
        if (review.getReviewer() != null && review.getReviewer().getId().equals(reporterId)) {
            throw new BadRequestException("You cannot report your own review");
        }

        if (reportRepository.existsByReviewIdAndReporterId(reviewId, reporterId)) {
            return;
        }

        try {
            ReviewReport report = new ReviewReport();
            report.setReviewId(reviewId);
            report.setReporterId(reporterId);
            report.setReason(request.getReason());
            report.setDetail(request.getDetail());
            reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException dup) {
            return;
        }

        reviewRepository.incrementReportCount(reviewId);

        int newReportCount = review.getReportCount() + 1;
        if (newReportCount >= autoHideThreshold && review.getStatus() == ReviewStatus.PUBLISHED) {
            reviewRepository.updateStatusIfDifferent(reviewId, ReviewStatus.PENDING_MODERATION.name());
            review.setStatus(ReviewStatus.PENDING_MODERATION);

            UUID cid = review.getProduct().getCompany().getId();
            UUID pid = review.getProduct().getId();
            boolean hasMedia = mediaRepository.countByReviewId(reviewId) > 0;
            evictAfterCommit(() -> {
                singleFlightCache.evictByPattern("reviews:" + cid + ":" + pid + ":*");
                singleFlightCache.evict("reviews:summary:" + cid + ":" + pid);
                reviewIndexingService.indexReview(review, hasMedia);
            });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ReviewReportResponse> listReports(ReportStatus status, int page, int size) {
        int clampedSize = Math.min(Math.max(1, size), 50);
        Pageable pageable = PageRequest.of(Math.max(0, page), clampedSize,
                Sort.by(Sort.Direction.ASC, "createdAt"));
        ReportStatus filterStatus = status != null ? status : ReportStatus.OPEN;
        Page<ReviewReport> reports = reportRepository.findByStatus(filterStatus, pageable);

        List<UUID> reviewIds = reports.map(ReviewReport::getReviewId).getContent();
        Map<UUID, ProductReview> reviewById = new HashMap<>();
        for (ProductReview r : reviewRepository.findAllById(reviewIds)) reviewById.put(r.getId(), r);

        Map<UUID, List<ReviewMediaResponse>> mediaByReview = new HashMap<>();
        if (!reviewIds.isEmpty()) {
            for (ReviewMedia m : mediaRepository.findByReviewIdInOrderByReviewIdAscPositionAsc(reviewIds)) {
                mediaByReview.computeIfAbsent(m.getReviewId(), k -> new java.util.ArrayList<>())
                        .add(new ReviewMediaResponse(m.getId(), m.getUrl(), m.getPosition()));
            }
        }

        return new PagedResponse<>(reports.map(r -> toResponse(r,
                reviewById.get(r.getReviewId()),
                mediaByReview.getOrDefault(r.getReviewId(), List.of()))));
    }

    @Override
    @Transactional
    public void moderate(UUID reviewId, UUID moderatorId, ModerateReviewRequest request) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        ModerationAction action = request.getAction();
        ReviewStatus targetStatus = switch (action) {
            case PUBLISH -> ReviewStatus.PUBLISHED;
            case HIDE -> ReviewStatus.HIDDEN;
            case REMOVE -> ReviewStatus.REMOVED;
        };
        reviewRepository.updateStatusIfDifferent(reviewId, targetStatus.name());
        review.setStatus(targetStatus);

        ReportStatus resolution = action == ModerationAction.PUBLISH
                ? ReportStatus.DISMISSED
                : ReportStatus.ACTIONED;
        reportRepository.resolveOpenReportsForReview(reviewId, resolution, Instant.now(), moderatorId);

        UUID cid = review.getProduct().getCompany().getId();
        UUID pid = review.getProduct().getId();
        boolean hasMedia = mediaRepository.countByReviewId(reviewId) > 0;
        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("reviews:" + cid + ":" + pid + ":*");
            singleFlightCache.evict("reviews:summary:" + cid + ":" + pid);
            if (review.getReviewer() != null) {
                singleFlightCache.evict("review:me:" + cid + ":" + pid + ":" + review.getReviewer().getId());
            }
            if (action == ModerationAction.REMOVE) {
                reviewIndexingService.removeReview(reviewId);
            } else {
                reviewIndexingService.indexReview(review, hasMedia);
            }
        });
    }

    private ReviewReportResponse toResponse(ReviewReport report, ProductReview review, List<ReviewMediaResponse> media) {
        String reviewerName = "";
        if (review != null && review.getReviewer() != null) {
            User u = review.getReviewer();
            reviewerName = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                    + (u.getLastName() == null ? "" : u.getLastName())).trim();
        }

        return new ReviewReportResponse(
                report.getId(),
                report.getReviewId(),
                report.getReporterId(),
                report.getReason().name(),
                report.getDetail(),
                report.getStatus().name(),
                report.getCreatedAt(),
                report.getResolvedAt(),
                report.getResolvedBy(),
                review != null && review.getProduct() != null ? review.getProduct().getId() : null,
                review != null && review.getReviewer() != null ? review.getReviewer().getId() : null,
                reviewerName,
                review != null ? review.getRating() : 0,
                review != null ? review.getTitle() : null,
                review != null ? review.getBody() : null,
                review != null && review.getStatus() != null ? review.getStatus().name() : null,
                review != null ? review.getReportCount() : 0,
                media
        );
    }

    private void evictAfterCommit(Runnable eviction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { eviction.run(); }
            });
        } else {
            eviction.run();
        }
    }
}
