package backend.kafka.consumers;

import backend.events.announcement.AnnouncementPublishedEvent;
import backend.events.email.EmailEvent;
import backend.models.core.CompanyFollow;
import backend.repositories.CompanyFollowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AnnouncementFanoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementFanoutConsumer.class);
    private static final int FOLLOWER_PAGE_SIZE = 500;

    private final CompanyFollowRepository followRepository;
    private final KafkaTemplate<String, EmailEvent> emailKafkaTemplate;
    private final String emailTopic;

    public AnnouncementFanoutConsumer(
            CompanyFollowRepository followRepository,
            KafkaTemplate<String, EmailEvent> emailKafkaTemplate,
            @Value("${app.kafka.topics.email-events}") String emailTopic) {
        this.followRepository = followRepository;
        this.emailKafkaTemplate = emailKafkaTemplate;
        this.emailTopic = emailTopic;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.announcement-events}",
            groupId = "announcement-fanout",
            containerFactory = "announcementKafkaListenerContainerFactory"
    )
    public void onAnnouncementPublished(AnnouncementPublishedEvent event) {
        int pageNumber = 0;
        boolean hasMore = true;

        while (hasMore) {
            Page<CompanyFollow> page = followRepository
                    .findAllByCompanyIdAndNotificationsEnabledTrue(
                            event.companyId(), PageRequest.of(pageNumber, FOLLOWER_PAGE_SIZE));

            for (CompanyFollow follow : page.getContent()) {
                try {
                    var user = follow.getUser();
                    EmailEvent email = new EmailEvent.AnnouncementEmail(
                            user.getEmail(),
                            user.getFirstName(),
                            event.companyId(),
                            event.companyName(),
                            event.announcementId(),
                            event.title(),
                            event.body(),
                            event.announcementType()
                    );
                    emailKafkaTemplate.send(emailTopic, user.getId().toString(), email);
                } catch (Exception e) {
                    log.warn("fanout: failed to enqueue email for follow={} announcement={}",
                            follow.getId(), event.announcementId(), e);
                }
            }

            hasMore = page.hasNext();
            pageNumber++;
        }
    }
}
