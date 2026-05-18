package backend.controllers.impl.subscriptions;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.subscription.CreateSubscriptionRequest;
import backend.dtos.requests.subscription.UpdateSubscriptionRequest;
import backend.dtos.responses.subscription.SavedPaymentMethodResponse;
import backend.dtos.responses.subscription.SetupIntentResponse;
import backend.dtos.responses.subscription.SubscriptionResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.subscriptions.SubscriptionService;

import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/subscriptions")
@RequireAuth
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    // -------------------------------------------------------------------------
    // Saved payment methods
    // -------------------------------------------------------------------------

    @PostMapping("/setup-intent")
    public ResponseEntity<SetupIntentResponse> createSetupIntent() {
        try {
            return ResponseEntity.ok(subscriptionService.createSetupIntent(resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/payment-methods")
    public ResponseEntity<List<SavedPaymentMethodResponse>> listPaymentMethods() {
        try {
            return ResponseEntity.ok(subscriptionService.listPaymentMethods(resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/payment-methods/{id}")
    public ResponseEntity<Void> detachPaymentMethod(@PathVariable UUID id) {
        try {
            subscriptionService.detachPaymentMethod(resolveUserId(), id);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // -------------------------------------------------------------------------
    // Subscription lifecycle
    // -------------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<SubscriptionResponse> create(@Valid @RequestBody CreateSubscriptionRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(subscriptionService.create(resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping
    public ResponseEntity<List<SubscriptionResponse>> list() {
        try {
            return ResponseEntity.ok(subscriptionService.listForUser(resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> get(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(subscriptionService.get(resolveUserId(), id));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubscriptionRequest request) {
        try {
            return ResponseEntity.ok(subscriptionService.update(resolveUserId(), id, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<SubscriptionResponse> pause(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(subscriptionService.pause(resolveUserId(), id));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<SubscriptionResponse> resume(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(subscriptionService.resume(resolveUserId(), id));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{id}/skip-next")
    public ResponseEntity<SubscriptionResponse> skipNext(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(subscriptionService.skipNext(resolveUserId(), id));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> cancel(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean atPeriodEnd) {
        try {
            return ResponseEntity.ok(subscriptionService.cancel(resolveUserId(), id, atPeriodEnd));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
