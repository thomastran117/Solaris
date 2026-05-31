package backend.controllers.impl.customers;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.credit.IssueCreditRequest;
import backend.dtos.responses.credit.CreditBalanceResponse;
import backend.dtos.responses.credit.CreditEntryResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.customers.CustomerCreditService;

@RestController
public class CustomerCreditController {

    private final CustomerCreditService creditService;

    public CustomerCreditController(CustomerCreditService creditService) {
        this.creditService = creditService;
    }

    @GetMapping("/me/credits")
    @RequireAuth
    public ResponseEntity<CreditBalanceResponse> getMyCredits() {
        try {
            return ResponseEntity.ok(creditService.getBalance(resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/support/customers/{userId}/credits")
    @RequireAuth(roles = {"SUPPORT", "MODERATOR", "ADMIN"})
    public ResponseEntity<CreditBalanceResponse> getCustomerCredits(@PathVariable UUID userId) {
        try {
            return ResponseEntity.ok(creditService.getBalance(userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/support/customers/{userId}/credits")
    @RequireAuth(roles = {"SUPPORT", "MODERATOR", "ADMIN"})
    public ResponseEntity<CreditEntryResponse> issueCredit(@PathVariable UUID userId,
                                                            @Valid @RequestBody IssueCreditRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(creditService.issueCredit(userId, request, resolveUserId(), null, null));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/support/credits/{entryId}/reverse")
    @RequireAuth(roles = {"SUPPORT", "MODERATOR", "ADMIN"})
    public ResponseEntity<CreditEntryResponse> reverseCredit(@PathVariable UUID entryId) {
        try {
            return ResponseEntity.ok(creditService.reverseCredit(entryId, resolveUserId()));
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
