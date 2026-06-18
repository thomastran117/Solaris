package backend.dtos.requests;

import backend.models.enums.WebhookEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public class RegisterWebhookRequest {

    @NotBlank
    @Size(max = 2048)
    @Pattern(regexp = "https?://.*", message = "URL must start with http:// or https://")
    private String url;

    @NotEmpty
    private Set<WebhookEventType> events;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Set<WebhookEventType> getEvents() { return events; }
    public void setEvents(Set<WebhookEventType> events) { this.events = events; }
}
