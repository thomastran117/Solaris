package backend.controllers.impl.company;

import backend.dtos.responses.product.CatalogSearchResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.products.ProductService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Public, anonymous-safe storefront search for a single company at
 * {@code /c/{id}}. Mirrors {@code MarketplaceCatalogController} but scopes
 * the ES query to {@code companyId} only.
 */
@Validated
@RestController
@RequestMapping("/companies/{companyId}/catalog")
public class CompanyCatalogController {

    private final ProductService productService;

    public CompanyCatalogController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/search")
    public ResponseEntity<CatalogSearchResponse> searchCatalog(
            @PathVariable long companyId,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) @Size(max = 100) String category,
            @RequestParam(required = false) @Size(max = 100) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "createdAt") @Pattern(regexp = "^[a-zA-Z.]+$", message = "Invalid sort field") String sort,
            @RequestParam(defaultValue = "desc") @Pattern(regexp = "^(?i)(asc|desc)$", message = "Direction must be asc or desc") String direction) {
        try {
            return ResponseEntity.ok(productService.searchCompanyCatalog(
                    companyId, q, category, brand, minPrice, maxPrice,
                    page, size, sort, direction));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }
}
