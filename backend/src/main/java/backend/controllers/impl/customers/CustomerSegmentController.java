package backend.controllers.impl.customers;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.segment.CreateCustomerSegmentRequest;
import backend.dtos.requests.segment.UpdateCustomerSegmentRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.segment.CustomerSegmentResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.customers.CustomerSegmentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/admin")
public class CustomerSegmentController {

    private final CustomerSegmentService segmentService;

    public CustomerSegmentController(CustomerSegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @GetMapping("/customer-segments")
    @RequireAuth(roles = {"ADMIN"})
    public ResponseEntity<PagedResponse<CustomerSegmentResponse>> listSegments(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(segmentService.listSegments(page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/customer-segments/{segmentId}")
    @RequireAuth(roles = {"ADMIN"})
    public ResponseEntity<CustomerSegmentResponse> getSegment(@PathVariable UUID segmentId) {
        try {
            return ResponseEntity.ok(segmentService.getSegment(segmentId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/customer-segments")
    @RequireAuth(roles = {"ADMIN"})
    public ResponseEntity<CustomerSegmentResponse> createSegment(
            @Valid @RequestBody CreateCustomerSegmentRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(segmentService.createSegment(request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/customer-segments/{segmentId}")
    @RequireAuth(roles = {"ADMIN"})
    public ResponseEntity<CustomerSegmentResponse> updateSegment(
            @PathVariable UUID segmentId,
            @Valid @RequestBody UpdateCustomerSegmentRequest request) {
        try {
            return ResponseEntity.ok(segmentService.updateSegment(segmentId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/customer-segments/{segmentId}")
    @RequireAuth(roles = {"ADMIN"})
    public ResponseEntity<Void> deleteSegment(@PathVariable UUID segmentId) {
        try {
            segmentService.deleteSegment(segmentId);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/users/{userId}/segments/{segmentId}")
    @RequireAuth(roles = {"ADMIN"})
    public ResponseEntity<Void> assignSegment(
            @PathVariable UUID userId,
            @PathVariable UUID segmentId) {
        try {
            segmentService.assignSegmentToUser(userId, segmentId);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/users/{userId}/segments/{segmentId}")
    @RequireAuth(roles = {"ADMIN"})
    public ResponseEntity<Void> removeSegment(
            @PathVariable UUID userId,
            @PathVariable UUID segmentId) {
        try {
            segmentService.removeSegmentFromUser(userId, segmentId);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }
}
