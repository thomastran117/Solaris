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
import backend.models.enums.CompanyCapability;
import backend.repositories.CompanyAnnouncementRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.company.CompanyAnnouncementService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class CompanyAnnouncementServiceImpl implements CompanyAnnouncementService {

    private final CompanyAnnouncementRepository announcementRepository;
    private final CompanyAccessService companyAccessService;
    private final AnnouncementEventPublisher eventPublisher;
    private final int rateLimitPerHour;

    public CompanyAnnouncementServiceImpl(
            CompanyAnnouncementRepository announcementRepository,
            CompanyAccessService companyAccessService,
            AnnouncementEventPublisher eventPublisher,
            @Value("${app.announcements.rate-limit.per-hour:10}") int rateLimitPerHour) {
        this.announcementRepository = announcementRepository;
        this.companyAccessService = companyAccessService;
        this.eventPublisher = eventPublisher;
        this.rateLimitPerHour = rateLimitPerHour;
    }

    @Override
    @Transactional
    public AnnouncementResponse create(UUID companyId, UUID userId, CreateAnnouncementRequest request) {
        Company company = companyAccessService.require(companyId, userId, CompanyCapability.MANAGE_PRODUCTS);

        long recentCount = announcementRepository.countByCompanyIdAndCreatedAtAfter(
                companyId, Instant.now().minus(1, ChronoUnit.HOURS));
        if (recentCount >= rateLimitPerHour) {
            throw new TooManyRequestException("Announcement rate limit exceeded. Max " + rateLimitPerHour + " per hour.");
        }

        CompanyAnnouncement announcement = new CompanyAnnouncement();
        announcement.setCompany(company);
        announcement.setTitle(request.getTitle());
        announcement.setBody(request.getBody());
        announcement.setType(request.getType());
        announcement.setStatus(AnnouncementStatus.DRAFT);
        announcement.setProductId(request.getProductId());

        return toResponse(announcementRepository.save(announcement));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AnnouncementResponse> listPublished(UUID companyId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("publishedAt").descending());
        Page<AnnouncementResponse> result = announcementRepository
                .findAllByCompanyIdAndStatus(companyId, AnnouncementStatus.PUBLISHED, pageable)
                .map(this::toResponse);
        return new PagedResponse<>(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AnnouncementResponse> listAll(UUID companyId, UUID userId, int page, int size) {
        companyAccessService.require(companyId, userId, CompanyCapability.READ_PRODUCTS);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AnnouncementResponse> result = announcementRepository
                .findAllByCompanyId(companyId, pageable)
                .map(this::toResponse);
        return new PagedResponse<>(result);
    }

    @Override
    @Transactional
    public AnnouncementResponse update(UUID companyId, UUID announcementId, UUID userId,
                                       UpdateAnnouncementRequest request) {
        companyAccessService.require(companyId, userId, CompanyCapability.MANAGE_PRODUCTS);

        CompanyAnnouncement announcement = announcementRepository.findByIdAndCompanyId(announcementId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found"));

        if (request.getTitle() != null) announcement.setTitle(request.getTitle());
        if (request.getBody() != null) announcement.setBody(request.getBody());
        if (request.getType() != null) announcement.setType(request.getType());
        if (request.getProductId() != null) announcement.setProductId(request.getProductId());

        boolean shouldPublish = Boolean.TRUE.equals(request.getPublish())
                && announcement.getStatus() == AnnouncementStatus.DRAFT;
        if (shouldPublish) {
            announcement.setStatus(AnnouncementStatus.PUBLISHED);
            announcement.setPublishedAt(Instant.now());
        }

        CompanyAnnouncement saved = announcementRepository.save(announcement);

        if (shouldPublish) {
            eventPublisher.publish(new AnnouncementPublishedEvent(
                    saved.getId(),
                    saved.getCompany().getId(),
                    saved.getCompany().getName(),
                    saved.getTitle(),
                    saved.getBody(),
                    saved.getType(),
                    saved.getPublishedAt()
            ));
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID companyId, UUID announcementId, UUID userId) {
        companyAccessService.require(companyId, userId, CompanyCapability.MANAGE_PRODUCTS);

        CompanyAnnouncement announcement = announcementRepository.findByIdAndCompanyId(announcementId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found"));

        if (announcement.getStatus() == AnnouncementStatus.PUBLISHED) {
            throw new BadRequestException("Cannot delete a published announcement");
        }

        announcementRepository.delete(announcement);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AnnouncementResponse> getFollowingFeed(UUID userId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<AnnouncementResponse> result = announcementRepository
                .findFeedForUser(userId, pageable)
                .map(this::toResponse);
        return new PagedResponse<>(result);
    }

    private AnnouncementResponse toResponse(CompanyAnnouncement a) {
        Company c = a.getCompany();
        return new AnnouncementResponse(
                a.getId(),
                c.getId(),
                c.getName(),
                c.getLogoUrl(),
                a.getTitle(),
                a.getBody(),
                a.getType() != null ? a.getType().name() : null,
                a.getStatus() != null ? a.getStatus().name() : null,
                a.getProductId(),
                a.getPublishedAt(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
