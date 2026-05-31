package backend.services.intf;

import backend.dtos.requests.product.CreateProductRequest;
import backend.dtos.requests.product.UpdateProductRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.ProductResponse;
import backend.models.enums.ProductStatus;

import java.math.BigDecimal;
import java.util.List;

public interface ProductService {
    PagedResponse<ProductResponse> searchProducts(long companyId, String q, String category, String brand, BigDecimal minPrice, BigDecimal maxPrice, Boolean featured, ProductStatus status, int page, int size, String sort, String direction);
    ProductResponse getProduct(long companyId, long productId);
    List<ProductResponse> getProductsByIds(long companyId, List<Long> ids);
    ProductResponse createProduct(long companyId, long ownerId, CreateProductRequest request);
    ProductResponse updateProduct(long companyId, long productId, long ownerId, UpdateProductRequest request);
    void deleteProduct(long companyId, long productId, long ownerId);
}
