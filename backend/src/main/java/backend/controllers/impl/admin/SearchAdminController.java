package backend.controllers.impl.admin;

import backend.dtos.responses.general.MessageResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.kafka.workers.IndexVersionManager;
import backend.kafka.workers.ProductIndexingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/search")
public class SearchAdminController {

    private final IndexVersionManager indexVersionManager;
    private final ProductIndexingService productIndexingService;

    public SearchAdminController(IndexVersionManager indexVersionManager,
                                  ProductIndexingService productIndexingService) {
        this.indexVersionManager = indexVersionManager;
        this.productIndexingService = productIndexingService;
    }

    /**
     * Rolls over the products index to apply updated analyzer settings, then triggers
     * a full reindex to populate new fields (e.g. nameCompletion). Run once after deploying
     * mapping changes.
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> reindex() {
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
