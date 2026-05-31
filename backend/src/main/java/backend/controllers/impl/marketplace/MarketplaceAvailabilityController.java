package backend.controllers.impl.marketplace;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import backend.dtos.responses.inventory.AvailabilityEstimateResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.inventory.AvailabilityEstimateService;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * Public storefront endpoint that powers the product-page availability/delivery panel.
 * Buyer coordinates are optional and never logged at full precision.
 */
@RestController
@RequestMapping("/marketplaces/{marketplaceId}/catalog/products/{productId}")
@Validated
public class MarketplaceAvailabilityController {

    private final AvailabilityEstimateService availabilityEstimateService;

    public MarketplaceAvailabilityController(AvailabilityEstimateService availabilityEstimateService) {
        this.availabilityEstimateService = availabilityEstimateService;
    }

    @GetMapping("/availability")
    public ResponseEntity<AvailabilityEstimateResponse> getAvailability(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID variantId,
            @RequestParam(required = false)
            @DecimalMin(value = "-90.0",  message = "Latitude must be >= -90")
            @DecimalMax(value = "90.0",   message = "Latitude must be <= 90") Double lat,
            @RequestParam(required = false)
            @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
            @DecimalMax(value = "180.0",  message = "Longitude must be <= 180") Double lng) {
        try {
            if ((lat == null) ^ (lng == null)) {
                throw new BadRequestException("lat and lng must be supplied together");
            }
            return ResponseEntity.ok(availabilityEstimateService.estimateForMarketplace(
                    marketplaceId, productId, variantId, lat, lng));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }
}
