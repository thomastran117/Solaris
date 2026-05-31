package backend.services.impl.company;

import backend.dtos.requests.company.CreateAnnouncementRequest;
import backend.dtos.requests.company.UpdateAnnouncementRequest;
import backend.dtos.responses.company.AnnouncementResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.events.announcement.AnnouncementPublishedEvent;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.exceptions.http.TooManyRequestException;
import backend.kafka.producers.AnnouncementEventPublisher;
import backend.models.core.Company;
import backend.models.core.CompanyAnnouncement;
import backend.models.enums.AnnouncementStatus;
import backend.models.enums.AnnouncementType;
import backend.repositories.CompanyAnnouncementRepository;
import backend.services.intf.company.CompanyAccessService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CompanyAnnouncementServiceImplTest {

    private CompanyAnnouncementRepository announcementRepository;
    private CompanyAccessService companyAccessService;
    private AnnouncementEventPublisher eventPublisher;
    private CompanyAnnouncementServiceImpl service;

    private static final UUID USER_ID           = TestIds.uuid(1);
    private static final UUID COMPANY_ID        = TestIds.uuid(2);
    private static final UUID ANNOUNCEMENT_ID   = TestIds.uuid(3);
    private static final int  RATE_LIMIT        = 10;

    @BeforeEach
    void setUp() {
        announcementRepository = mock(CompanyAnnouncementRepository.class);
        companyAccessService   = mock(CompanyAccessService.class);
        eventPublisher         = mock(AnnouncementEventPublisher.class);
        service = new CompanyAnnouncementServiceImpl(
                announcementRepository, companyAccessService, eventPublisher, RATE_LIMIT);
    }

    // ─── create ──────────────────────────────────────────────────────────────

    @Test
    void create_happyPath_savesDraftWithCorrectFields() {
        Company company = makeCompany(COMPANY_ID);
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);
        when(announcementRepository.countByCompanyIdAndCreatedAtAfter(eq(COMPANY_ID), any()))
                .thenReturn(0L);
        CompanyAnnouncement saved = makeDraftAnnouncement(ANNOUNCEMENT_ID, company);
        when(announcementRepository.save(any())).thenReturn(saved);

        CreateAnnouncementRequest req = makeCreateRequest("Flash Sale", "50% off everything", AnnouncementType.SALE);
        AnnouncementResponse result = service.create(COMPANY_ID, USER_ID, req);

        assertEquals(ANNOUNCEMENT_ID, result.getId());
        assertEquals("DRAFT", result.getStatus());
        ArgumentCaptor<CompanyAnnouncement> captor = ArgumentCaptor.forClass(CompanyAnnouncement.class);
        verify(announcementRepository).save(captor.capture());
        assertEquals("Flash Sale", captor.getValue().getTitle());
        assertEquals(AnnouncementStatus.DRAFT, captor.getValue().getStatus());
    }

    @Test
    void create_rateLimitExceeded_throwsTooManyRequests() {
        Company company = makeCompany(COMPANY_ID);
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);
        when(announcementRepository.countByCompanyIdAndCreatedAtAfter(eq(COMPANY_ID), any()))
                .thenReturn((long) RATE_LIMIT);

        assertThrows(TooManyRequestException.class,
                () -> service.create(COMPANY_ID, USER_ID,
                        makeCreateRequest("Test", "Body", AnnouncementType.GENERAL)));
        verify(announcementRepository, never()).save(any());
    }

    @Test
    void create_belowRateLimit_allowsCreation() {
        Company company = makeCompany(COMPANY_ID);
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);
        when(announcementRepository.countByCompanyIdAndCreatedAtAfter(eq(COMPANY_ID), any()))
                .thenReturn((long) RATE_LIMIT - 1);
        CompanyAnnouncement saved = makeDraftAnnouncement(ANNOUNCEMENT_ID, company);
        when(announcementRepository.save(any())).thenReturn(saved);

        assertDoesNotThrow(() -> service.create(COMPANY_ID, USER_ID,
                makeCreateRequest("Test", "Body", AnnouncementType.GENERAL)));
        verify(announcementRepository).save(any());
    }

    // ─── update ──────────────────────────────────────────────────────────────

    @Test
    void update_mutableFields_updatesWithoutPublishing() {
        Company company = makeCompany(COMPANY_ID);
        CompanyAnnouncement ann = makeDraftAnnouncement(ANNOUNCEMENT_ID, company);
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);
        when(announcementRepository.findByIdAndCompanyId(ANNOUNCEMENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(ann));
        when(announcementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateAnnouncementRequest req = new UpdateAnnouncementRequest();
        req.setTitle("Updated Title");
        req.setBody("New body");
        service.update(COMPANY_ID, ANNOUNCEMENT_ID, USER_ID, req);

        ArgumentCaptor<CompanyAnnouncement> captor = ArgumentCaptor.forClass(CompanyAnnouncement.class);
        verify(announcementRepository).save(captor.capture());
        assertEquals("Updated Title", captor.getValue().getTitle());
        assertEquals("New body", captor.getValue().getBody());
        assertEquals(AnnouncementStatus.DRAFT, captor.getValue().getStatus());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void update_publishTrue_onDraft_flipsToPublishedAndPublishesEvent() {
        Company company = makeCompany(COMPANY_ID);
        CompanyAnnouncement ann = makeDraftAnnouncement(ANNOUNCEMENT_ID, company);
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);
        when(announcementRepository.findByIdAndCompanyId(ANNOUNCEMENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(ann));
        when(announcementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateAnnouncementRequest req = new UpdateAnnouncementRequest();
        req.setPublish(true);
        service.update(COMPANY_ID, ANNOUNCEMENT_ID, USER_ID, req);

        ArgumentCaptor<CompanyAnnouncement> savedCaptor = ArgumentCaptor.forClass(CompanyAnnouncement.class);
        verify(announcementRepository).save(savedCaptor.capture());
        assertEquals(AnnouncementStatus.PUBLISHED, savedCaptor.getValue().getStatus());
        assertNotNull(savedCaptor.getValue().getPublishedAt());

        verify(eventPublisher).publish(any(AnnouncementPublishedEvent.class));
    }

    @Test
    void update_publishTrue_onAlreadyPublished_isNoOp() {
        Company company = makeCompany(COMPANY_ID);
        CompanyAnnouncement ann = makeDraftAnnouncement(ANNOUNCEMENT_ID, company);
        ann.setStatus(AnnouncementStatus.PUBLISHED);
        ann.setPublishedAt(Instant.now().minusSeconds(3600));
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);
        when(announcementRepository.findByIdAndCompanyId(ANNOUNCEMENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(ann));
        when(announcementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateAnnouncementRequest req = new UpdateAnnouncementRequest();
        req.setPublish(true);
        service.update(COMPANY_ID, ANNOUNCEMENT_ID, USER_ID, req);

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void update_announcementNotFound_throwsResourceNotFound() {
        Company company = makeCompany(COMPANY_ID);
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);
        when(announcementRepository.findByIdAndCompanyId(ANNOUNCEMENT_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.update(COMPANY_ID, ANNOUNCEMENT_ID, USER_ID, new UpdateAnnouncementRequest()));
    }

    // ─── delete ──────────────────────────────────────────────────────────────

    @Test
    void delete_draft_deletesSuccessfully() {
        Company company = makeCompany(COMPANY_ID);
        CompanyAnnouncement ann = makeDraftAnnouncement(ANNOUNCEMENT_ID, company);
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);
        when(announcementRepository.findByIdAndCompanyId(ANNOUNCEMENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(ann));

        service.delete(COMPANY_ID, ANNOUNCEMENT_ID, USER_ID);

        verify(announcementRepository).delete(ann);
    }

    @Test
    void delete_published_throwsBadRequest() {
        Company company = makeCompany(COMPANY_ID);
        CompanyAnnouncement ann = makeDraftAnnouncement(ANNOUNCEMENT_ID, company);
        ann.setStatus(AnnouncementStatus.PUBLISHED);
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);
        when(announcementRepository.findByIdAndCompanyId(ANNOUNCEMENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(ann));

        assertThrows(BadRequestException.class,
                () -> service.delete(COMPANY_ID, ANNOUNCEMENT_ID, USER_ID));
        verify(announcementRepository, never()).delete(any());
    }

    @Test
    void delete_notFound_throwsResourceNotFound() {
        Company company = makeCompany(COMPANY_ID);
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);
        when(announcementRepository.findByIdAndCompanyId(ANNOUNCEMENT_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.delete(COMPANY_ID, ANNOUNCEMENT_ID, USER_ID));
    }

    // ─── listPublished ───────────────────────────────────────────────────────

    @Test
    void listPublished_delegatesToRepositoryWithPublishedStatus() {
        Company company = makeCompany(COMPANY_ID);
        CompanyAnnouncement ann = makeDraftAnnouncement(ANNOUNCEMENT_ID, company);
        ann.setStatus(AnnouncementStatus.PUBLISHED);
        when(announcementRepository.findAllByCompanyIdAndStatus(
                eq(COMPANY_ID), eq(AnnouncementStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ann)));

        PagedResponse<AnnouncementResponse> result = service.listPublished(COMPANY_ID, 0, 20);

        assertEquals(1, result.getItems().size());
        verify(announcementRepository).findAllByCompanyIdAndStatus(
                eq(COMPANY_ID), eq(AnnouncementStatus.PUBLISHED), any(Pageable.class));
        verify(companyAccessService, never()).require(any(), any(), any());
    }

    // ─── listAll ─────────────────────────────────────────────────────────────

    @Test
    void listAll_delegatesToRepositoryWithAccessCheck() {
        Company company = makeCompany(COMPANY_ID);
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);
        when(announcementRepository.findAllByCompanyId(eq(COMPANY_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listAll(COMPANY_ID, USER_ID, 0, 20);

        verify(announcementRepository).findAllByCompanyId(eq(COMPANY_ID), any(Pageable.class));
    }

    // ─── getFollowingFeed ────────────────────────────────────────────────────

    @Test
    void getFollowingFeed_delegatesToFeedQuery() {
        Company company = makeCompany(COMPANY_ID);
        CompanyAnnouncement ann = makeDraftAnnouncement(ANNOUNCEMENT_ID, company);
        ann.setStatus(AnnouncementStatus.PUBLISHED);
        when(announcementRepository.findFeedForUser(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ann)));

        PagedResponse<AnnouncementResponse> result = service.getFollowingFeed(USER_ID, 0, 20);

        assertEquals(1, result.getItems().size());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Company makeCompany(UUID id) {
        Company c = new Company();
        c.setId(id);
        c.setName("Test Company");
        return c;
    }

    private CompanyAnnouncement makeDraftAnnouncement(UUID id, Company company) {
        CompanyAnnouncement a = new CompanyAnnouncement();
        a.setId(id);
        a.setCompany(company);
        a.setTitle("Test Announcement");
        a.setBody("Test body content");
        a.setType(AnnouncementType.GENERAL);
        a.setStatus(AnnouncementStatus.DRAFT);
        return a;
    }

    private CreateAnnouncementRequest makeCreateRequest(String title, String body, AnnouncementType type) {
        CreateAnnouncementRequest req = new CreateAnnouncementRequest();
        req.setTitle(title);
        req.setBody(body);
        req.setType(type);
        return req;
    }
}
