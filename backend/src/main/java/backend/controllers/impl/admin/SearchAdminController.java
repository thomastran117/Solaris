package backend.controllers.impl.admin;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.responses.general.MessageResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.kafka.workers.IndexVersionManager;
import backend.kafka.workers.ProductIndexingService;
import backend.services.intf.RateLimitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/search")
public class SearchAdminController {

    private final IndexVersionManager indexVersionManager;
    private final ProductIndexingService productIndexingService;
    private final RateLimitService rateLimitService;

    public SearchAdminController(IndexVersionManager indexVersionManager,
                                  ProductIndexingService productIndexingService,
                                  RateLimitService rateLimitService) {
        this.indexVersionManager = indexVersionManager;
        this.productIndexingService = productIndexingService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * Rolls over the products index to apply updated analyzer settings, then triggers
     * a full reindex to populate new fields (e.g. nameCompletion). Run once after deploying
     * mapping changes.
     */
    @PostMapping("/reindex")
    @RequireAuth(roles = {"ADMIN"})
    public ResponseEntity<MessageResponse> reindex() {
        // Rate-limit to 1 reindex per hour globally — a full reindex is expensive and
        // should not be triggered more than once per deployment window regardless of
        // which admin account initiates it.
        rateLimitService.enforce("admin:reindex", "global", 1, 3600);
        try {
            indexVersionManager.rolloverIndex("products");
            productIndexingService.reindexAll();
            return ResponseEntity.ok(new MessageResponse("Index rollover and full reindex triggered successfully"));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }
}
