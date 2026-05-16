package backend.kafka.consumers;

import backend.events.email.EmailEvent;
import backend.kafka.workers.EmailSender;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EmailKafkaConsumer {

    private final EmailSender sender;

    public EmailKafkaConsumer(EmailSender sender) {
        this.sender = sender;
    }

    @KafkaListener(topics = "${app.kafka.topics.email-events}", groupId = "email-worker",
                   containerFactory = "emailKafkaListenerContainerFactory")
    public void onEmailEvent(EmailEvent event) {
        sender.send(event);
    }
}
