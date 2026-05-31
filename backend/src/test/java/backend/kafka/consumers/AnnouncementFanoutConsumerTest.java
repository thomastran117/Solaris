package backend.kafka.consumers;

import backend.events.announcement.AnnouncementPublishedEvent;
import backend.events.email.EmailEvent;
import backend.models.core.Company;
import backend.models.core.CompanyFollow;
import backend.models.core.User;
import backend.models.enums.AnnouncementType;
import backend.repositories.CompanyFollowRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnnouncementFanoutConsumerTest {

    private CompanyFollowRepository followRepository;
    private KafkaTemplate<String, EmailEvent> emailKafkaTemplate;
    private AnnouncementFanoutConsumer consumer;

    private static final UUID COMPANY_ID      = TestIds.uuid(1);
    private static final UUID ANNOUNCEMENT_ID = TestIds.uuid(2);
    private static final String EMAIL_TOPIC   = "email-events";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        followRepository    = mock(CompanyFollowRepository.class);
        emailKafkaTemplate  = mock(KafkaTemplate.class);
        consumer = new AnnouncementFanoutConsumer(followRepository, emailKafkaTemplate, EMAIL_TOPIC);

        when(emailKafkaTemplate.send(any(String.class), any(String.class), any(EmailEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    // ─── onAnnouncementPublished ──────────────────────────────────────────────

    @Test
    void onAnnouncementPublished_singlePage_sendsEmailForEachFollower() {
        List<CompanyFollow> followers = List.of(
                makeFollow(TestIds.uuid(10), true),
                makeFollow(TestIds.uuid(11), true),
                makeFollow(TestIds.uuid(12), true));

        Page<CompanyFollow> page = new PageImpl<>(followers, PageRequest.of(0, 500), 3);
        when(followRepository.findAllByCompanyIdAndNotificationsEnabledTrue(eq(COMPANY_ID), any()))
                .thenReturn(page);

        consumer.onAnnouncementPublished(makeEvent());

        verify(emailKafkaTemplate, times(3))
                .send(eq(EMAIL_TOPIC), any(String.class), any(EmailEvent.AnnouncementEmail.class));
    }

    @Test
    void onAnnouncementPublished_notificationsDisabled_followersSkipped() {
        // Repository already filters by notificationsEnabled=true, so an empty page
        // represents followers who are all muted.
        Page<CompanyFollow> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 500), 0);
        when(followRepository.findAllByCompanyIdAndNotificationsEnabledTrue(eq(COMPANY_ID), any()))
                .thenReturn(emptyPage);

        consumer.onAnnouncementPublished(makeEvent());

        verify(emailKafkaTemplate, never())
                .send(any(String.class), any(String.class), any(EmailEvent.class));
    }

    @Test
    void onAnnouncementPublished_multiPage_iteratesBothPagesAndSendsAllEmails() {
        List<CompanyFollow> firstPageFollowers = List.of(
                makeFollow(TestIds.uuid(10), true),
                makeFollow(TestIds.uuid(11), true));
        List<CompanyFollow> secondPageFollowers = List.of(
                makeFollow(TestIds.uuid(12), true));

        // total=501 so hasNext()=true for page 0 (size 500, ceil(501/500)=2 pages)
        Page<CompanyFollow> firstPage  = new PageImpl<>(firstPageFollowers,  PageRequest.of(0, 500), 501);
        Page<CompanyFollow> secondPage = new PageImpl<>(secondPageFollowers, PageRequest.of(1, 500), 501);

        when(followRepository.findAllByCompanyIdAndNotificationsEnabledTrue(eq(COMPANY_ID), any()))
                .thenReturn(firstPage, secondPage);

        consumer.onAnnouncementPublished(makeEvent());

        verify(emailKafkaTemplate, times(3))
                .send(eq(EMAIL_TOPIC), any(String.class), any(EmailEvent.AnnouncementEmail.class));
    }

    @Test
    void onAnnouncementPublished_perFollowerExceptionSuppressed_remainingFollowersStillProcessed() {
        List<CompanyFollow> followers = List.of(
                makeFollow(TestIds.uuid(10), true),
                makeFollow(TestIds.uuid(11), true),
                makeFollow(TestIds.uuid(12), true));

        Page<CompanyFollow> page = new PageImpl<>(followers, PageRequest.of(0, 500), 3);
        when(followRepository.findAllByCompanyIdAndNotificationsEnabledTrue(eq(COMPANY_ID), any()))
                .thenReturn(page);

        // Make the second follower's send throw
        when(emailKafkaTemplate.send(any(String.class), any(String.class), any(EmailEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null))
                .thenThrow(new RuntimeException("Kafka unavailable"))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> consumer.onAnnouncementPublished(makeEvent()));

        // First and third followers still get their email
        verify(emailKafkaTemplate, times(3))
                .send(eq(EMAIL_TOPIC), any(String.class), any(EmailEvent.AnnouncementEmail.class));
    }

    @Test
    void onAnnouncementPublished_correctEmailContents() {
        CompanyFollow follow = makeFollow(TestIds.uuid(10), true);
        Page<CompanyFollow> page = new PageImpl<>(List.of(follow), PageRequest.of(0, 500), 1);
        when(followRepository.findAllByCompanyIdAndNotificationsEnabledTrue(eq(COMPANY_ID), any()))
                .thenReturn(page);

        consumer.onAnnouncementPublished(makeEvent());

        ArgumentCaptor<EmailEvent> captor = ArgumentCaptor.forClass(EmailEvent.class);
        verify(emailKafkaTemplate).send(eq(EMAIL_TOPIC), any(String.class), captor.capture());

        EmailEvent.AnnouncementEmail email = (EmailEvent.AnnouncementEmail) captor.getValue();
        assertEquals("follower@test.com", email.toEmail());
        assertEquals(COMPANY_ID, email.companyId());
        assertEquals("Test Company", email.companyName());
        assertEquals("Summer Sale!", email.announcementTitle());
        assertEquals(AnnouncementType.SALE, email.announcementType());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private AnnouncementPublishedEvent makeEvent() {
        return new AnnouncementPublishedEvent(
                ANNOUNCEMENT_ID,
                COMPANY_ID,
                "Test Company",
                "Summer Sale!",
                "50% off all items this weekend.",
                AnnouncementType.SALE,
                Instant.now()
        );
    }

    private CompanyFollow makeFollow(UUID followId, boolean notificationsEnabled) {
        User user = new User();
        user.setId(TestIds.uuid(99));
        user.setEmail("follower@test.com");
        user.setFirstName("Alice");

        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setName("Test Company");

        CompanyFollow follow = new CompanyFollow();
        follow.setId(followId);
        follow.setUser(user);
        follow.setCompany(company);
        follow.setFollowedAt(Instant.now());
        follow.setNotificationsEnabled(notificationsEnabled);
        return follow;
    }
}
