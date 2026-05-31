package backend.controllers.impl.company;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.company.CreateAnnouncementRequest;
import backend.dtos.requests.company.UpdateAnnouncementRequest;
import backend.dtos.responses.company.AnnouncementResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.company.CompanyAnnouncementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
public class CompanyAnnouncementController {

    private final CompanyAnnouncementService announcementService;

    public CompanyAnnouncementController(CompanyAnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @PostMapping("/companies/{id}/announcements")
    @RequireAuth
    public ResponseEntity<AnnouncementResponse> create(
            @PathVariable UUID id,
            @Valid @RequestBody CreateAnnouncementRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(announcementService.create(id, resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/companies/{id}/announcements")
    public ResponseEntity<PagedResponse<AnnouncementResponse>> listPublished(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(announcementService.listPublished(id, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/companies/{id}/announcements/all")
    @RequireAuth
    public ResponseEntity<PagedResponse<AnnouncementResponse>> listAll(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(announcementService.listAll(id, resolveUserId(), page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/companies/{id}/announcements/{annId}")
    @RequireAuth
    public ResponseEntity<AnnouncementResponse> update(
            @PathVariable UUID id,
            @PathVariable UUID annId,
            @Valid @RequestBody UpdateAnnouncementRequest request) {
        try {
            return ResponseEntity.ok(announcementService.update(id, annId, resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/companies/{id}/announcements/{annId}")
    @RequireAuth
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @PathVariable UUID annId) {
        try {
            announcementService.delete(id, annId, resolveUserId());
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
