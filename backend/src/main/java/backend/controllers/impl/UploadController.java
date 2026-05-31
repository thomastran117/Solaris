package backend.controllers.impl;

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
import backend.services.intf.StorageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/upload")
public class UploadController {

    private final StorageService storageService;

    public UploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping("/presign")
    @RequireAuth
    public ResponseEntity<PresignUploadResponse> presign(@Valid @RequestBody PresignUploadRequest request) {
        try {
            long userId = resolveUserId();
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
