package backend.controllers.impl.customers;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.responses.company.AnnouncementResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.company.CompanyAnnouncementService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
public class CustomerFeedController {

    private final CompanyAnnouncementService announcementService;

    public CustomerFeedController(CompanyAnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping("/me/announcement-feed")
    @RequireAuth
    public ResponseEntity<PagedResponse<AnnouncementResponse>> getAnnouncementFeed(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(announcementService.getFollowingFeed(userId, page, size));
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
