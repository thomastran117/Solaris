package backend.dtos.responses;

public record WebhookCreationResponse(
        WebhookSubscriptionResponse subscription,
        String secret
) {}
