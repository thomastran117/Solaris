package backend.controllers.impl.webhook;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.RegisterWebhookRequest;
import backend.dtos.responses.WebhookCreationResponse;
import backend.dtos.responses.WebhookDeliveryLogResponse;
import backend.dtos.responses.WebhookSubscriptionResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.WebhookSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/companies/{companyId}/webhooks")
public class CompanyWebhookController {

    private final WebhookSubscriptionService webhookService;

    public CompanyWebhookController(WebhookSubscriptionService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    @RequireAuth
    public ResponseEntity<WebhookCreationResponse> register(
            @PathVariable UUID companyId,
            @Valid @RequestBody RegisterWebhookRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(webhookService.register(companyId, resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping
    @RequireAuth
    public ResponseEntity<List<WebhookSubscriptionResponse>> list(
            @PathVariable UUID companyId) {
        try {
            return ResponseEntity.ok(webhookService.listSubscriptions(companyId, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{id}")
    @RequireAuth
    public ResponseEntity<Void> delete(
            @PathVariable UUID companyId,
            @PathVariable UUID id) {
        try {
            webhookService.deleteSubscription(id, companyId, resolveUserId());
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{id}/verify")
    @RequireAuth
    public ResponseEntity<Void> verify(
            @PathVariable UUID companyId,
            @PathVariable UUID id) {
        try {
            webhookService.verify(id, companyId, resolveUserId());
            return ResponseEntity.ok().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{id}/deliveries")
    @RequireAuth
    public ResponseEntity<PagedResponse<WebhookDeliveryLogResponse>> deliveries(
            @PathVariable UUID companyId,
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        try {
            Page<WebhookDeliveryLogResponse> page =
                    webhookService.getDeliveries(id, companyId, resolveUserId(), pageable);
            return ResponseEntity.ok(new PagedResponse<>(page));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
