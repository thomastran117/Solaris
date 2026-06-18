package backend.services.intf;

import backend.dtos.requests.RegisterWebhookRequest;
import backend.dtos.responses.WebhookCreationResponse;
import backend.dtos.responses.WebhookDeliveryLogResponse;
import backend.dtos.responses.WebhookSubscriptionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface WebhookSubscriptionService {

    WebhookCreationResponse register(UUID companyId, UUID userId, RegisterWebhookRequest request);

    void verify(UUID subscriptionId, UUID companyId, UUID userId);

    void disable(UUID subscriptionId, UUID companyId, UUID userId);

    void deleteSubscription(UUID subscriptionId, UUID companyId, UUID userId);

    List<WebhookSubscriptionResponse> listSubscriptions(UUID companyId, UUID userId);

    Page<WebhookDeliveryLogResponse> getDeliveries(UUID subscriptionId, UUID companyId, UUID userId, Pageable pageable);
}
