package backend.services.impl;

import backend.dtos.requests.report.CreateReportRequest;
import backend.dtos.requests.report.ResolveReportRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.report.ReportResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.exceptions.http.TooManyRequestException;
import backend.models.core.Company;
import backend.models.core.ProductReview;
import backend.models.core.Report;
import backend.models.core.ReportScreenshot;
import backend.models.core.User;
import backend.models.enums.ReportStatus;
import backend.models.enums.ReportTargetType;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReportRepository;
import backend.repositories.UserRepository;
import backend.kafka.workers.ReportIndexingService;
import backend.services.intf.ReportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReportServiceImpl implements ReportService {

    private static final int RATE_LIMIT = 10;
    private static final int RATE_WINDOW_HOURS = 24;

    private final ReportRepository reportRepository;
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final ProductReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ReportIndexingService reportIndexingService;

    public ReportServiceImpl(
            ReportRepository reportRepository,
            ProductRepository productRepository,
            CompanyRepository companyRepository,
            ProductReviewRepository reviewRepository,
            UserRepository userRepository,
            ReportIndexingService reportIndexingService) {
        this.reportRepository = reportRepository;
        this.productRepository = productRepository;
        this.companyRepository = companyRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.reportIndexingService = reportIndexingService;
    }

    @Override
    @Transactional
    public ReportResponse create(UUID reporterId, CreateReportRequest request) {
        long recentCount = reportRepository.countByReporterIdAndCreatedAtAfter(
                reporterId, Instant.now().minusSeconds(RATE_WINDOW_HOURS * 3600L));
        if (recentCount >= RATE_LIMIT) {
            throw new TooManyRequestException("You have submitted too many reports. Please wait before submitting more.");
        }

        if (reportRepository.existsByTargetTypeAndTargetIdAndReporterId(
                request.getTargetType(), request.getTargetId(), reporterId)) {
            throw new ConflictException("You have already reported this.");
        }

        String targetName = resolveTargetName(request.getTargetType(), request.getTargetId(), reporterId);

        Report report = new Report();
        report.setTargetType(request.getTargetType());
        report.setTargetId(request.getTargetId());
        report.setReporterId(reporterId);
        report.setReason(request.getReason());
        report.setTitle(request.getTitle());
        report.setDescription(request.getDescription());

        if (request.getScreenshotUrls() != null) {
            List<String> urls = request.getScreenshotUrls();
            for (int i = 0; i < urls.size(); i++) {
                report.getScreenshots().add(new ReportScreenshot(report, urls.get(i), i));
            }
        }

        report = reportRepository.save(report);

        indexAfterSave(report);

        String reporterName = resolveReporterName(reporterId);
        return new ReportResponse(report, targetName, reporterName);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ReportResponse> listReports(ReportTargetType targetType, ReportStatus status, int page, int size) {
        int clampedSize = Math.min(Math.max(1, size), 50);
        Pageable pageable = PageRequest.of(Math.max(0, page), clampedSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Report> reports;
        if (targetType != null && status != null) {
            reports = reportRepository.findByTargetTypeAndStatus(targetType, status, pageable);
        } else if (targetType != null) {
            reports = reportRepository.findByTargetType(targetType, pageable);
        } else if (status != null) {
            reports = reportRepository.findByStatus(status, pageable);
        } else {
            reports = reportRepository.findAll(pageable);
        }

        return new PagedResponse<>(reports.map(r -> {
            String targetName = resolveTargetNameQuietly(r.getTargetType(), r.getTargetId());
            String reporterName = resolveReporterName(r.getReporterId());
            return new ReportResponse(r, targetName, reporterName);
        }));
    }

    @Override
    @Transactional(readOnly = true)
    public ReportResponse getReport(UUID reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        String targetName = resolveTargetNameQuietly(report.getTargetType(), report.getTargetId());
        String reporterName = resolveReporterName(report.getReporterId());
        return new ReportResponse(report, targetName, reporterName);
    }

    @Override
    @Transactional
    public void resolve(UUID reportId, UUID moderatorId, ResolveReportRequest request) {
        if (request.getResolution() == ReportStatus.OPEN) {
            throw new BadRequestException("Resolution must be ACTIONED or DISMISSED");
        }
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new BadRequestException("Report has already been resolved");
        }
        report.setStatus(request.getResolution());
        report.setResolvedAt(Instant.now());
        report.setResolvedBy(moderatorId);
        report = reportRepository.save(report);

        indexAfterSave(report);
    }

    // -- helpers --

    private String resolveTargetName(ReportTargetType type, UUID targetId, UUID reporterId) {
        return switch (type) {
            case PRODUCT -> productRepository.findById(targetId)
                    .map(p -> p.getName())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            case COMPANY -> companyRepository.findById(targetId)
                    .map(Company::getName)
                    .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
            case REVIEW -> {
                ProductReview review = reviewRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
                if (review.getReviewer() != null && review.getReviewer().getId().equals(reporterId)) {
                    throw new BadRequestException("You cannot report your own review");
                }
                yield "Review by " + displayName(review.getReviewer());
            }
            case USER -> {
                if (targetId.equals(reporterId)) {
                    throw new BadRequestException("You cannot report yourself");
                }
                User user = userRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                yield "User: " + displayName(user);
            }
        };
    }

    private String resolveTargetNameQuietly(ReportTargetType type, UUID targetId) {
        return switch (type) {
            case PRODUCT -> productRepository.findById(targetId)
                    .map(p -> p.getName()).orElse("Deleted product");
            case COMPANY -> companyRepository.findById(targetId)
                    .map(Company::getName).orElse("Deleted company");
            case REVIEW -> reviewRepository.findById(targetId)
                    .map(r -> "Review by " + displayName(r.getReviewer())).orElse("Deleted review");
            case USER -> userRepository.findById(targetId)
                    .map(u -> "User: " + displayName(u)).orElse("Deleted user");
        };
    }

    private String resolveReporterName(UUID reporterId) {
        return userRepository.findById(reporterId)
                .map(this::displayName)
                .orElse("Unknown");
    }

    private String displayName(User user) {
        if (user == null) return "Unknown";
        String first = user.getFirstName() == null ? "" : user.getFirstName();
        String last = user.getLastName() == null ? "" : user.getLastName();
        return (first + " " + last).trim();
    }

    private void indexAfterSave(Report report) {
        reportIndexingService.indexReport(report);
    }
}
