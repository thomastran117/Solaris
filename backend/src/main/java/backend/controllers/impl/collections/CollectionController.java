package backend.controllers.impl.collections;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.collection.AddCollectionProductRequest;
import backend.dtos.requests.collection.CreateCollectionRequest;
import backend.dtos.requests.collection.UpdateCollectionProductRequest;
import backend.dtos.requests.collection.UpdateCollectionRequest;
import backend.dtos.responses.collection.CollectionProductResponse;
import backend.dtos.responses.collection.CollectionResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.models.enums.CollectionStatus;
import backend.models.enums.CollectionType;
import backend.services.intf.collections.CollectionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/companies/{companyId}/collections")
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<CollectionResponse>> listCollections(
            @PathVariable UUID companyId,
            @RequestParam(required = false) CollectionType type,
            @RequestParam(required = false) CollectionStatus status,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(collectionService.listCollections(companyId, type, status, featured, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{collectionId}")
    public ResponseEntity<CollectionResponse> getCollection(
            @PathVariable UUID companyId,
            @PathVariable UUID collectionId) {
        try {
            return ResponseEntity.ok(collectionService.getCollection(companyId, collectionId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping
    @RequireAuth
    public ResponseEntity<CollectionResponse> createCollection(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateCollectionRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(collectionService.createCollection(companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{collectionId}")
    @RequireAuth
    public ResponseEntity<CollectionResponse> updateCollection(
            @PathVariable UUID companyId,
            @PathVariable UUID collectionId,
            @Valid @RequestBody UpdateCollectionRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(collectionService.updateCollection(companyId, collectionId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{collectionId}")
    @RequireAuth
    public ResponseEntity<Void> deleteCollection(
            @PathVariable UUID companyId,
            @PathVariable UUID collectionId) {
        try {
            UUID userId = resolveUserId();
            collectionService.deleteCollection(companyId, collectionId, userId);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // --- Membership ---

    @GetMapping("/{collectionId}/products")
    public ResponseEntity<PagedResponse<CollectionProductResponse>> listProducts(
            @PathVariable UUID companyId,
            @PathVariable UUID collectionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        try {
            return ResponseEntity.ok(collectionService.listCollectionProducts(companyId, collectionId, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{collectionId}/products")
    @RequireAuth
    public ResponseEntity<CollectionProductResponse> addProduct(
            @PathVariable UUID companyId,
            @PathVariable UUID collectionId,
            @Valid @RequestBody AddCollectionProductRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(collectionService.addCollectionProduct(companyId, collectionId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{collectionId}/products/{productId}")
    @RequireAuth
    public ResponseEntity<CollectionProductResponse> updateProduct(
            @PathVariable UUID companyId,
            @PathVariable UUID collectionId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateCollectionProductRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(collectionService.updateCollectionProduct(
                    companyId, collectionId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{collectionId}/products/{productId}")
    @RequireAuth
    public ResponseEntity<Void> removeProduct(
            @PathVariable UUID companyId,
            @PathVariable UUID collectionId,
            @PathVariable UUID productId) {
        try {
            UUID userId = resolveUserId();
            collectionService.removeCollectionProduct(companyId, collectionId, productId, userId);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{collectionId}/refresh")
    @RequireAuth
    public ResponseEntity<CollectionResponse> refreshCollection(
            @PathVariable UUID companyId,
            @PathVariable UUID collectionId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(collectionService.refreshCollection(companyId, collectionId, userId));
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
