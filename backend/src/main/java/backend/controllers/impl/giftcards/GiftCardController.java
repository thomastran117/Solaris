package backend.controllers.impl.giftcards;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.giftcard.RedeemGiftCardRequest;
import backend.dtos.responses.giftcard.GiftCardBalanceResponse;
import backend.dtos.responses.giftcard.GiftCardResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.giftcards.GiftCardService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/gift-cards")
public class GiftCardController {

    private final GiftCardService giftCardService;

    public GiftCardController(GiftCardService giftCardService) {
        this.giftCardService = giftCardService;
    }

    @PostMapping("/redeem")
    @RequireAuth
    public ResponseEntity<GiftCardResponse> redeem(@Valid @RequestBody RedeemGiftCardRequest request) {
        try {
            return ResponseEntity.ok(giftCardService.redeemCode(request.code(), resolveUserId(), request.amountCents()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{code}/balance")
    public ResponseEntity<GiftCardBalanceResponse> getBalance(@PathVariable String code) {
        try {
            return ResponseEntity.ok(giftCardService.getBalance(code));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping
    @RequireAuth
    public ResponseEntity<Page<GiftCardResponse>> listPurchased(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(giftCardService.listPurchased(resolveUserId(), PageRequest.of(page, size)));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/admin/{id}/void")
    @RequireAuth(roles = {"ADMIN", "MODERATOR"})
    public ResponseEntity<Void> voidCard(@PathVariable UUID id) {
        try {
            giftCardService.voidCard(id);
            return ResponseEntity.noContent().build();
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
