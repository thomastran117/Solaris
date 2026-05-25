package backend.services.impl;

import backend.dtos.requests.report.CreateReportRequest;
import backend.dtos.requests.report.ResolveReportRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.report.ReportResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.exceptions.http.TooManyRequestException;
import backend.kafka.workers.ReportIndexingService;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductReview;
import backend.models.core.Report;
import backend.models.core.User;
import backend.models.enums.ReportReason;
import backend.models.enums.ReportStatus;
import backend.models.enums.ReportTargetType;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReportRepository;
import backend.repositories.UserRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportServiceImplTest {

    private static final UUID REPORTER_ID = TestIds.uuid(1);
    private static final UUID ADMIN_ID    = TestIds.uuid(2);
    private static final UUID REPORT_ID   = TestIds.uuid(3);
    private static final UUID TARGET_ID   = TestIds.uuid(4);
    private static final UUID OTHER_ID    = TestIds.uuid(5);

    private ReportRepository reportRepository;
    private ProductRepository productRepository;
    private CompanyRepository companyRepository;
    private ProductReviewRepository reviewRepository;
    private UserRepository userRepository;
    private ReportIndexingService reportIndexingService;

    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        reportRepository     = mock(ReportRepository.class);
        productRepository    = mock(ProductRepository.class);
        companyRepository    = mock(CompanyRepository.class);
        reviewRepository     = mock(ProductReviewRepository.class);
        userRepository       = mock(UserRepository.class);
        reportIndexingService = mock(ReportIndexingService.class);

        service = new ReportServiceImpl(
                reportRepository, productRepository, companyRepository,
                reviewRepository, userRepository, reportIndexingService);

        // default: no rate-limit hit, no duplicate
        when(reportRepository.countByReporterIdAndCreatedAtAfter(eq(REPORTER_ID), any(Instant.class)))
                .thenReturn(0L);
        when(reportRepository.existsByTargetTypeAndTargetIdAndReporterId(any(), any(), any()))
                .thenReturn(false);

        // save returns the same report with IDs set (simulates DB generation)
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            if (r.getId() == null) r.setId(REPORT_ID);
            if (r.getCreatedAt() == null) r.setCreatedAt(Instant.now());
            for (int i = 0; i < r.getScreenshots().size(); i++) {
                if (r.getScreenshots().get(i).getId() == null) {
                    r.getScreenshots().get(i).setId(TestIds.uuid(100 + i));
                }
            }
            return r;
        });

        when(userRepository.findById(REPORTER_ID)).thenReturn(Optional.of(makeUser(REPORTER_ID, "Alice", "Smith")));
    }

    // ─── create — PRODUCT target ──────────────────────────────────────────────

    @Test
    void create_productTarget_savesReportAndIndexes() {
        Product product = mock(Product.class);
        when(product.getName()).thenReturn("Blue Widget");
        when(productRepository.findById(TARGET_ID)).thenReturn(Optional.of(product));

        ReportResponse result = service.create(REPORTER_ID, makeRequest(ReportTargetType.PRODUCT, TARGET_ID));

        assertNotNull(result);
        assertEquals(REPORT_ID.toString(), result.getId());
        assertEquals(ReportTargetType.PRODUCT, result.getTargetType());
        assertEquals("Blue Widget", result.getTargetName());
        assertEquals("Alice Smith", result.getReporterName());
        verify(reportRepository).save(any(Report.class));
        verify(reportIndexingService).indexReport(any(Report.class));
    }

    @Test
    void create_productTarget_withScreenshots_attachesScreenshots() {
        Product product = mock(Product.class);
        when(product.getName()).thenReturn("Widget");
        when(productRepository.findById(TARGET_ID)).thenReturn(Optional.of(product));

        CreateReportRequest req = makeRequest(ReportTargetType.PRODUCT, TARGET_ID);
        req.setScreenshotUrls(List.of("https://cdn.example.com/s1.png", "https://cdn.example.com/s2.png"));

        service.create(REPORTER_ID, req);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getScreenshots().size());
    }

    @Test
    void create_productNotFound_throwsResourceNotFound() {
        when(productRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.create(REPORTER_ID, makeRequest(ReportTargetType.PRODUCT, TARGET_ID)));
    }

    // ─── create — COMPANY target ──────────────────────────────────────────────

    @Test
    void create_companyTarget_resolvesCompanyName() {
        Company company = mock(Company.class);
        when(company.getName()).thenReturn("Acme Corp");
        when(companyRepository.findById(TARGET_ID)).thenReturn(Optional.of(company));

        ReportResponse result = service.create(REPORTER_ID, makeRequest(ReportTargetType.COMPANY, TARGET_ID));

        assertEquals("Acme Corp", result.getTargetName());
        verify(reportRepository).save(any(Report.class));
    }

    @Test
    void create_companyNotFound_throwsResourceNotFound() {
        when(companyRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.create(REPORTER_ID, makeRequest(ReportTargetType.COMPANY, TARGET_ID)));
    }

    // ─── create — REVIEW target ───────────────────────────────────────────────

    @Test
    void create_reviewTarget_resolvesReviewerName() {
        User reviewer = makeUser(OTHER_ID, "Bob", "Jones");
        ProductReview review = mock(ProductReview.class);
        when(review.getReviewer()).thenReturn(reviewer);
        when(reviewRepository.findById(TARGET_ID)).thenReturn(Optional.of(review));

        ReportResponse result = service.create(REPORTER_ID, makeRequest(ReportTargetType.REVIEW, TARGET_ID));

        assertEquals("Review by Bob Jones", result.getTargetName());
    }

    @Test
    void create_reviewTarget_selfReport_throwsBadRequest() {
        // reporter is the reviewer
        User reviewer = makeUser(REPORTER_ID, "Alice", "Smith");
        ProductReview review = mock(ProductReview.class);
        when(review.getReviewer()).thenReturn(reviewer);
        when(reviewRepository.findById(TARGET_ID)).thenReturn(Optional.of(review));

        assertThrows(BadRequestException.class,
                () -> service.create(REPORTER_ID, makeRequest(ReportTargetType.REVIEW, TARGET_ID)));
    }

    @Test
    void create_reviewNotFound_throwsResourceNotFound() {
        when(reviewRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.create(REPORTER_ID, makeRequest(ReportTargetType.REVIEW, TARGET_ID)));
    }

    // ─── create — USER target ─────────────────────────────────────────────────

    @Test
    void create_userTarget_resolvesUserName() {
        User target = makeUser(TARGET_ID, "Charlie", "Brown");
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

        ReportResponse result = service.create(REPORTER_ID, makeRequest(ReportTargetType.USER, TARGET_ID));

        assertEquals("User: Charlie Brown", result.getTargetName());
    }

    @Test
    void create_userTarget_selfReport_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> service.create(REPORTER_ID, makeRequest(ReportTargetType.USER, REPORTER_ID)));
    }

    @Test
    void create_userTargetNotFound_throwsResourceNotFound() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.create(REPORTER_ID, makeRequest(ReportTargetType.USER, TARGET_ID)));
    }

    // ─── create — rate limiting & duplicate guard ─────────────────────────────

    @Test
    void create_rateLimitExceeded_throwsTooManyRequest() {
        when(reportRepository.countByReporterIdAndCreatedAtAfter(eq(REPORTER_ID), any(Instant.class)))
                .thenReturn(10L);

        assertThrows(TooManyRequestException.class,
                () -> service.create(REPORTER_ID, makeRequest(ReportTargetType.PRODUCT, TARGET_ID)));

        verify(reportRepository, never()).save(any());
    }

    @Test
    void create_duplicate_throwsConflict() {
        when(reportRepository.existsByTargetTypeAndTargetIdAndReporterId(any(), any(), any()))
                .thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.create(REPORTER_ID, makeRequest(ReportTargetType.PRODUCT, TARGET_ID)));

        verify(reportRepository, never()).save(any());
    }

    // ─── listReports ──────────────────────────────────────────────────────────

    @Test
    void listReports_noFilters_callsFindAll() {
        when(reportRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PagedResponse<ReportResponse> result = service.listReports(null, null, 0, 20);

        assertNotNull(result);
        verify(reportRepository).findAll(any(Pageable.class));
        verify(reportRepository, never()).findByStatus(any(), any());
        verify(reportRepository, never()).findByTargetType(any(), any());
    }

    @Test
    void listReports_targetTypeOnly_callsFindByTargetType() {
        when(reportRepository.findByTargetType(eq(ReportTargetType.PRODUCT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listReports(ReportTargetType.PRODUCT, null, 0, 20);

        verify(reportRepository).findByTargetType(eq(ReportTargetType.PRODUCT), any(Pageable.class));
    }

    @Test
    void listReports_statusOnly_callsFindByStatus() {
        when(reportRepository.findByStatus(eq(ReportStatus.OPEN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listReports(null, ReportStatus.OPEN, 0, 20);

        verify(reportRepository).findByStatus(eq(ReportStatus.OPEN), any(Pageable.class));
    }

    @Test
    void listReports_bothFilters_callsFindByTargetTypeAndStatus() {
        when(reportRepository.findByTargetTypeAndStatus(
                eq(ReportTargetType.COMPANY), eq(ReportStatus.OPEN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listReports(ReportTargetType.COMPANY, ReportStatus.OPEN, 0, 20);

        verify(reportRepository).findByTargetTypeAndStatus(
                eq(ReportTargetType.COMPANY), eq(ReportStatus.OPEN), any(Pageable.class));
    }

    @Test
    void listReports_sizeOverMax_clampedTo50() {
        when(reportRepository.findAll(any(Pageable.class))).thenAnswer(inv -> {
            Pageable p = inv.getArgument(0);
            return new PageImpl<>(List.of(), p, 0);
        });

        PagedResponse<ReportResponse> result = service.listReports(null, null, 0, 200);

        assertEquals(50, result.getSize());
    }

    // ─── getReport ────────────────────────────────────────────────────────────

    @Test
    void getReport_found_returnsResponse() {
        Report report = makeReport(ReportStatus.OPEN);
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));

        Product product = mock(Product.class);
        when(product.getName()).thenReturn("Widget");
        when(productRepository.findById(TARGET_ID)).thenReturn(Optional.of(product));

        ReportResponse result = service.getReport(REPORT_ID);

        assertNotNull(result);
        assertEquals(REPORT_ID.toString(), result.getId());
        assertEquals(ReportStatus.OPEN, result.getStatus());
    }

    @Test
    void getReport_notFound_throwsResourceNotFound() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getReport(REPORT_ID));
    }

    // ─── resolve ─────────────────────────────────────────────────────────────

    @Test
    void resolve_actioned_setsStatusAndTimestamp() {
        Report report = makeReport(ReportStatus.OPEN);
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ResolveReportRequest req = new ResolveReportRequest();
        req.setResolution(ReportStatus.ACTIONED);

        service.resolve(REPORT_ID, ADMIN_ID, req);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());

        Report saved = captor.getValue();
        assertEquals(ReportStatus.ACTIONED, saved.getStatus());
        assertEquals(ADMIN_ID, saved.getResolvedBy());
        assertNotNull(saved.getResolvedAt());
        verify(reportIndexingService).indexReport(any(Report.class));
    }

    @Test
    void resolve_dismissed_setsStatusDismissed() {
        Report report = makeReport(ReportStatus.OPEN);
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ResolveReportRequest req = new ResolveReportRequest();
        req.setResolution(ReportStatus.DISMISSED);

        service.resolve(REPORT_ID, ADMIN_ID, req);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertEquals(ReportStatus.DISMISSED, captor.getValue().getStatus());
    }

    @Test
    void resolve_resolutionIsOpen_throwsBadRequest() {
        ResolveReportRequest req = new ResolveReportRequest();
        req.setResolution(ReportStatus.OPEN);

        assertThrows(BadRequestException.class,
                () -> service.resolve(REPORT_ID, ADMIN_ID, req));

        verify(reportRepository, never()).findById(any());
    }

    @Test
    void resolve_reportNotFound_throwsResourceNotFound() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.empty());

        ResolveReportRequest req = new ResolveReportRequest();
        req.setResolution(ReportStatus.ACTIONED);

        assertThrows(ResourceNotFoundException.class,
                () -> service.resolve(REPORT_ID, ADMIN_ID, req));
    }

    @Test
    void resolve_alreadyResolved_throwsBadRequest() {
        Report report = makeReport(ReportStatus.ACTIONED);
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));

        ResolveReportRequest req = new ResolveReportRequest();
        req.setResolution(ReportStatus.DISMISSED);

        assertThrows(BadRequestException.class,
                () -> service.resolve(REPORT_ID, ADMIN_ID, req));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private CreateReportRequest makeRequest(ReportTargetType type, UUID targetId) {
        CreateReportRequest req = new CreateReportRequest();
        req.setTargetType(type);
        req.setTargetId(targetId);
        req.setReason(ReportReason.SPAM);
        req.setTitle("Spam content on listing");
        req.setDescription("This listing contains spam content repeated many times.");
        return req;
    }

    private Report makeReport(ReportStatus status) {
        Report r = new Report();
        r.setId(REPORT_ID);
        r.setTargetType(ReportTargetType.PRODUCT);
        r.setTargetId(TARGET_ID);
        r.setReporterId(REPORTER_ID);
        r.setReason(ReportReason.SPAM);
        r.setTitle("Spam content");
        r.setDescription("This listing contains spam content repeated many times.");
        r.setStatus(status);
        r.setCreatedAt(Instant.now());
        return r;
    }

    private User makeUser(UUID id, String firstName, String lastName) {
        User u = new User();
        u.setId(id);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setEmail(firstName.toLowerCase() + "@example.com");
        return u;
    }
}
