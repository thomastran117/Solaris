package backend.controllers.impl.products;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.upload.PresignUploadRequest;
import backend.dtos.responses.upload.PresignUploadResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.RateLimitService;
import backend.services.intf.StorageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/upload")
public class UploadController {

    private static final int PRESIGN_LIMIT = 20;
    private static final int PRESIGN_WINDOW_SECONDS = 60;

    private final StorageService storageService;
    private final RateLimitService rateLimitService;

    public UploadController(StorageService storageService, RateLimitService rateLimitService) {
        this.storageService = storageService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/presign")
    @RequireAuth
    public ResponseEntity<PresignUploadResponse> presign(@Valid @RequestBody PresignUploadRequest request) {
        try {
            long userId = resolveUserId();
            rateLimitService.enforce("upload:presign", Long.toString(userId), PRESIGN_LIMIT, PRESIGN_WINDOW_SECONDS);
            PresignUploadResponse response = storageService.generatePresignedUrl(
                    request.getFolder(),
                    userId,
                    request.getContentType()
            );
            return ResponseEntity.ok(response);
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ((Number) auth.getPrincipal()).longValue();
    }
}
