package backend.controllers.impl.savedlist;

import java.util.UUID;
import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.savedlist.AddSavedListItemRequest;
import backend.dtos.requests.savedlist.CreateSavedListRequest;
import backend.dtos.requests.savedlist.UpdateSavedListItemRequest;
import backend.dtos.requests.savedlist.UpdateSavedListRequest;
import backend.dtos.responses.savedlist.PublicSavedListResponse;
import backend.dtos.responses.savedlist.SavedListItemResponse;
import backend.dtos.responses.savedlist.SavedListResponse;
import backend.dtos.responses.savedlist.SavedListSummaryResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.models.core.SavedListType;
import backend.services.intf.savedlist.SavedListService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Note: {@link RequireAuth} is intentionally applied per-method rather than at class level
 * so that {@link #getPublicSavedList(String)} remains accessible without authentication.
 */
@RestController
@RequestMapping("/saved-lists")
public class SavedListController {

    private final SavedListService savedListService;

    public SavedListController(SavedListService savedListService) {
        this.savedListService = savedListService;
    }

    @GetMapping("")
    @RequireAuth
    public ResponseEntity<List<SavedListSummaryResponse>> listSavedLists(
            @RequestParam(value = "type", required = false) SavedListType type) {
        try {
            return ResponseEntity.ok(savedListService.listSavedLists(resolveUserId(), type));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{id}")
    @RequireAuth
    public ResponseEntity<SavedListResponse> getSavedList(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(savedListService.getSavedList(resolveUserId(), id));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/public/{slug}")
    public ResponseEntity<PublicSavedListResponse> getPublicSavedList(@PathVariable String slug) {
        try {
            return ResponseEntity.ok(savedListService.getPublicSavedList(slug));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("")
    @RequireAuth
    public ResponseEntity<SavedListResponse> createSavedList(
            @Valid @RequestBody CreateSavedListRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(savedListService.createSavedList(resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PutMapping("/{id}")
    @RequireAuth
    public ResponseEntity<SavedListResponse> updateSavedList(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSavedListRequest request) {
        try {
            return ResponseEntity.ok(savedListService.updateSavedList(resolveUserId(), id, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{id}")
    @RequireAuth
    public ResponseEntity<Void> deleteSavedList(@PathVariable UUID id) {
        try {
            savedListService.deleteSavedList(resolveUserId(), id);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{id}/items")
    @RequireAuth
    public ResponseEntity<SavedListItemResponse> addItem(
            @PathVariable UUID id,
            @Valid @RequestBody AddSavedListItemRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(savedListService.addItem(resolveUserId(), id, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{id}/items/{itemId}")
    @RequireAuth
    public ResponseEntity<SavedListItemResponse> updateItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateSavedListItemRequest request) {
        try {
            return ResponseEntity.ok(savedListService.updateItem(resolveUserId(), id, itemId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @RequireAuth
    public ResponseEntity<Void> removeItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        try {
            savedListService.removeItem(resolveUserId(), id, itemId);
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
