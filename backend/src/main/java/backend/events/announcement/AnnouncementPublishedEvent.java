package backend.events.announcement;

import backend.models.enums.AnnouncementType;

import java.time.Instant;
import java.util.UUID;

public record AnnouncementPublishedEvent(
        UUID announcementId,
        UUID companyId,
        String companyName,
        String title,
        String body,
        AnnouncementType announcementType,
        Instant publishedAt
) {}
