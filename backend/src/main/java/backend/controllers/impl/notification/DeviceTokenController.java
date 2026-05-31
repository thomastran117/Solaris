package backend.controllers.impl.notification;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.notification.RegisterDeviceTokenRequest;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.notification.DeviceTokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/devices")
@RequireAuth
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    public DeviceTokenController(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    @PostMapping("/push-token")
    public ResponseEntity<Void> registerPushToken(@Valid @RequestBody RegisterDeviceTokenRequest request) {
        try {
            deviceTokenService.registerToken(resolveUserId(), request.platform(), request.token());
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/push-token/{token}")
    public ResponseEntity<Void> revokePushToken(@PathVariable String token) {
        try {
            deviceTokenService.revokeToken(resolveUserId(), token);
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
