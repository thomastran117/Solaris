package backend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import backend.models.core.Product;
import backend.models.enums.ProductStatus;
import backend.repositories.projections.DailyDemandProjection;
import backend.repositories.projections.ProductDemandProjection;
import backend.repositories.projections.ProductSalesProjection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, java.util.UUID>, JpaSpecificationExecutor<Product> {
    Optional<Product> findByIdAndCompanyId(java.util.UUID id, java.util.UUID companyId);

    // -------------------------------------------------------------------------
    // Marketplace catalog queries
    // -------------------------------------------------------------------------

    @Query("SELECT p FROM Product p JOIN FETCH p.company WHERE p.marketplaceId = :marketplaceId AND p.marketplaceListed = true AND p.status = 'ACTIVE'")
    List<Product> findMarketplaceListed(@Param("marketplaceId") java.util.UUID marketplaceId);

    @Query(value = "SELECT p FROM Product p JOIN FETCH p.company WHERE p.marketplaceId = :marketplaceId AND p.marketplaceListed = true AND p.status = 'ACTIVE'",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE p.marketplaceId = :marketplaceId AND p.marketplaceListed = true AND p.status = 'ACTIVE'")
    Page<Product> findMarketplaceListedPaged(@Param("marketplaceId") java.util.UUID marketplaceId, Pageable pageable);

    Optional<Product> findByIdAndMarketplaceId(java.util.UUID id, java.util.UUID marketplaceId);

    List<Product> findAllByIdInAndMarketplaceId(Collection<java.util.UUID> ids, java.util.UUID marketplaceId);

    // -------------------------------------------------------------------------
    // JOIN FETCH queries used by indexing workers to avoid LazyInitializationException
    // on p.getCompany().getName() outside a JPA session
    // -------------------------------------------------------------------------

    @Query("SELECT p FROM Product p JOIN FETCH p.company")
    List<Product> findAllWithCompany();

    @Query("SELECT p FROM Product p JOIN FETCH p.company WHERE p.company.id = :companyId")
    List<Product> findAllByCompanyIdWithCompany(@Param("companyId") java.util.UUID companyId);

    /** Acquires a pessimistic write lock on the product row — use inside @Transactional to serialize concurrent mutations. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.company.id = :companyId")
    Optional<Product> findByIdAndCompanyIdWithLock(@Param("id") java.util.UUID id, @Param("companyId") java.util.UUID companyId);

    List<Product> findAllByCompanyId(java.util.UUID companyId);

    /**
     * Paginated catalogue listing for the company — used by export-to-CSV and other
     * large-result-set flows. The unpaginated overload above is OOM-prone for large
     * companies; prefer this method for new callers and migrate existing ones over time.
     */
    Page<Product> findAllByCompanyId(java.util.UUID companyId, Pageable pageable);

    /** Cheap upper-bound check so callers can short-circuit before paging through a huge result set. */
    long countByCompanyId(java.util.UUID companyId);

    /** Fetches a product with its company and the company's owner in one query — used by stock alert notifications. */
    @Query("SELECT p FROM Product p JOIN FETCH p.company c JOIN FETCH c.owner WHERE p.id = :id")
    Optional<Product> findByIdWithCompanyOwner(@Param("id") java.util.UUID id);
    List<Product> findAllByIdInAndCompanyId(Collection<java.util.UUID> ids, java.util.UUID companyId);
    boolean existsBySkuAndCompanyId(String sku, java.util.UUID companyId);
    Optional<Product> findBySkuAndCompanyId(String sku, java.util.UUID companyId);
    List<Product> findAllBySkuInAndCompanyId(Collection<String> skus, java.util.UUID companyId);

    /**
     * Atomically decrements stock. Returns 1 (success) when stock >= quantity or stock IS NULL
     * (untracked inventory), 0 when stock is tracked but insufficient.
     * NULL stock means no tracking — the decrement is a no-op (NULL - qty = NULL) but succeeds
     * so that untracked products can always be purchased.
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :id AND (p.stock IS NULL OR p.stock >= :quantity)")
    int decrementStock(@Param("id") java.util.UUID id, @Param("quantity") int quantity);

    /** Restores stock after a failed or cancelled order. */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity WHERE p.id = :id")
    int restoreStock(@Param("id") java.util.UUID id, @Param("quantity") int quantity);

    /**
     * Atomically applies a signed delta to stock. Prevents negative stock.
     * Returns 1 on success, 0 if the result would be negative or stock is null.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock + :delta WHERE p.id = :id AND p.stock IS NOT NULL AND (p.stock + :delta) >= 0")
    int adjustStock(@Param("id") java.util.UUID id, @Param("delta") int delta);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.company.id = :cid AND p.stock IS NOT NULL AND p.stock = 0")
    long countOutOfStock(@Param("cid") java.util.UUID companyId);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.company.id = :cid AND p.stock IS NOT NULL AND p.lowStockThreshold IS NOT NULL AND p.stock > 0 AND p.stock <= p.lowStockThreshold")
    long countLowStock(@Param("cid") java.util.UUID companyId);

    @Query("SELECT COALESCE(SUM(p.stock * p.price), 0) FROM Product p WHERE p.company.id = :cid AND p.stock IS NOT NULL AND p.stock > 0")
    BigDecimal totalInventoryValue(@Param("cid") java.util.UUID companyId);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.company.id = :cid AND p.stock IS NOT NULL")
    long countTrackedProducts(@Param("cid") java.util.UUID companyId);

    /**
     * Returns the top N products for a company ranked by total units sold across PAID orders.
     * Date range is applied inside the JOIN so products with no qualifying sales still appear
     * with totalUnitsSold = 0 rather than being excluded. Null from/to means no bound.
     */
    @Query(nativeQuery = true, value = """
            SELECT
                p.id          AS productId,
                p.name        AS productName,
                p.sku         AS sku,
                p.stock       AS currentStock,
                p.price       AS price,
                p.currency    AS currency,
                COALESCE(SUM(oi.quantity), 0)                    AS totalUnitsSold,
                COALESCE(SUM(oi.quantity * oi.unit_price), 0.00) AS totalRevenue
            FROM products p
            LEFT JOIN order_items oi ON oi.product_id = p.id
            LEFT JOIN orders o ON oi.order_id = o.id
                AND o.status = 'PAID'
                AND (:from IS NULL OR o.created_at >= :from)
                AND (:to   IS NULL OR o.created_at <= :to)
            WHERE p.company_id = :companyId
            GROUP BY p.id, p.name, p.sku, p.stock, p.price, p.currency
            ORDER BY totalUnitsSold DESC
            LIMIT :limit
            """)
    List<ProductSalesProjection> findTopByUnitsSold(
            @Param("companyId") java.util.UUID companyId,
            @Param("limit") int limit,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Returns the top N products for a company ranked by total revenue (unitPrice × quantity)
     * across PAID orders. Date range is applied inside the JOIN. Null from/to means no bound.
     */
    @Query(nativeQuery = true, value = """
            SELECT
                p.id          AS productId,
                p.name        AS productName,
                p.sku         AS sku,
                p.stock       AS currentStock,
                p.price       AS price,
                p.currency    AS currency,
                COALESCE(SUM(oi.quantity), 0)                    AS totalUnitsSold,
                COALESCE(SUM(oi.quantity * oi.unit_price), 0.00) AS totalRevenue
            FROM products p
            LEFT JOIN order_items oi ON oi.product_id = p.id
            LEFT JOIN orders o ON oi.order_id = o.id
                AND o.status = 'PAID'
                AND (:from IS NULL OR o.created_at >= :from)
                AND (:to   IS NULL OR o.created_at <= :to)
            WHERE p.company_id = :companyId
            GROUP BY p.id, p.name, p.sku, p.stock, p.price, p.currency
            ORDER BY totalRevenue DESC
            LIMIT :limit
            """)
    List<ProductSalesProjection> findTopByRevenue(
            @Param("companyId") java.util.UUID companyId,
            @Param("limit") int limit,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Returns products that have stock tracked and available (stock > 0) but have never
     * appeared in a PAID order — potential dead inventory.
     */
    @Query(nativeQuery = true, value = """
            SELECT
                p.id          AS productId,
                p.name        AS productName,
                p.sku         AS sku,
                p.stock       AS currentStock,
                p.price       AS price,
                p.currency    AS currency,
                0             AS totalUnitsSold,
                0.00          AS totalRevenue
            FROM products p
            WHERE p.company_id = :companyId
              AND p.stock IS NOT NULL
              AND p.stock > 0
              AND p.id NOT IN (
                    SELECT DISTINCT oi.product_id
                    FROM order_items oi
                    JOIN orders o ON oi.order_id = o.id
                    WHERE o.status = 'PAID'
              )
            ORDER BY p.stock DESC
            LIMIT :limit
            """)
    List<ProductSalesProjection> findNeverSold(
            @Param("companyId") java.util.UUID companyId,
            @Param("limit") int limit);

    /**
     * Returns the top products by units sold in PAID orders placed on or after :since,
     * scoped to :companyId. Bundle-only items (oi.product_id IS NULL) are excluded.
     * Used by DemandServiceImpl to compute 1-hour and 24-hour hot-product windows.
     */
    @Query(nativeQuery = true, value = """
            SELECT
                p.id                             AS productId,
                p.name                           AS productName,
                p.sku                            AS sku,
                p.price                          AS price,
                p.currency                       AS currency,
                SUM(oi.quantity)                 AS totalUnitsSold,
                SUM(oi.quantity * oi.unit_price) AS totalRevenue
            FROM products p
            JOIN order_items oi ON oi.product_id = p.id
            JOIN orders o       ON oi.order_id   = o.id
            WHERE p.company_id    = :companyId
              AND o.status        = 'PAID'
              AND o.created_at   >= :since
              AND oi.product_id  IS NOT NULL
            GROUP BY p.id, p.name, p.sku, p.price, p.currency
            ORDER BY totalUnitsSold DESC
            LIMIT :limit
            """)
    List<ProductDemandProjection> findTopByDemandSince(
            @Param("companyId") java.util.UUID companyId,
            @Param("since") Instant since,
            @Param("limit") int limit);

    /**
     * Returns one row per (product, calendar day UTC) with total units sold in PAID orders
     * placed on or after :since for the given company. Days with no sales are absent — callers
     * must zero-fill the series. Used by ForecastingService to compute daily demand baselines.
     */
    @Query(nativeQuery = true, value = """
            SELECT
                p.id                                    AS productId,
                CAST(DATE(o.created_at) AS DATE)        AS day,
                SUM(oi.quantity)                        AS units
            FROM products p
            JOIN order_items oi ON oi.product_id = p.id
            JOIN orders o       ON oi.order_id   = o.id
            WHERE p.company_id  = :companyId
              AND o.status      = 'PAID'
              AND o.created_at >= :since
              AND oi.product_id IS NOT NULL
            GROUP BY p.id, DATE(o.created_at)
            ORDER BY p.id, day
            """)
    List<DailyDemandProjection> findDailyDemandSince(
            @Param("companyId") java.util.UUID companyId,
            @Param("since") Instant since);

    /**
     * Same as findDailyDemandSince but bounded on both ends — used for the year-over-year
     * seasonal comparison window.
     */
    @Query(nativeQuery = true, value = """
            SELECT
                p.id                                    AS productId,
                CAST(DATE(o.created_at) AS DATE)        AS day,
                SUM(oi.quantity)                        AS units
            FROM products p
            JOIN order_items oi ON oi.product_id = p.id
            JOIN orders o       ON oi.order_id   = o.id
            WHERE p.company_id  = :companyId
              AND o.status      = 'PAID'
              AND o.created_at >= :from
              AND o.created_at <= :to
              AND oi.product_id IS NOT NULL
            GROUP BY p.id, DATE(o.created_at)
            ORDER BY p.id, day
            """)
    List<DailyDemandProjection> findDailyDemandBetween(
            @Param("companyId") java.util.UUID companyId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Returns distinct company IDs that have at least one PAID product order since :since.
     * Used by DemandTrackingScheduler to identify companies needing cache pre-warming.
     */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT p.company_id
            FROM products p
            JOIN order_items oi ON oi.product_id = p.id
            JOIN orders o       ON oi.order_id   = o.id
            WHERE o.status     = 'PAID'
              AND o.created_at >= :since
            """)
    List<java.util.UUID> findDistinctCompanyIdsWithPaidOrdersSince(@Param("since") Instant since);

    /**
     * Used by {@code ProductSchedulingWorker} to find scheduled products whose publish time has arrived.
     * Hits the {@code idx_products_status_scheduled_publish_at} index. JOIN FETCH on company so the
     * subsequent indexing call doesn't trigger a LazyInitializationException.
     */
    @Query("SELECT p FROM Product p JOIN FETCH p.company "
            + "WHERE p.status = :status AND p.scheduledPublishAt IS NOT NULL AND p.scheduledPublishAt <= :now")
    Page<Product> findDueForPublishing(
            @Param("status") ProductStatus status,
            @Param("now") Instant now,
            Pageable pageable);

    @Query(value = """
            SELECT p FROM Product p JOIN FETCH p.company
            WHERE p.marketplaceId = :marketplaceId
              AND p.status = backend.models.enums.ProductStatus.ACTIVE
              AND p.marketplaceListed = true
              AND (p.category IN :categories OR p.brand IN :brands)
              AND p.id NOT IN :excludedIds
            ORDER BY p.featured DESC, p.boostWeight DESC NULLS LAST, p.publishedAt DESC
            """,
           countQuery = """
            SELECT COUNT(p) FROM Product p
            WHERE p.marketplaceId = :marketplaceId
              AND p.status = backend.models.enums.ProductStatus.ACTIVE
              AND p.marketplaceListed = true
              AND (p.category IN :categories OR p.brand IN :brands)
              AND p.id NOT IN :excludedIds
            """)
    Page<Product> findPersonalizedFeed(
            @Param("marketplaceId") java.util.UUID marketplaceId,
            @Param("categories") Collection<String> categories,
            @Param("brands") Collection<String> brands,
            @Param("excludedIds") Collection<java.util.UUID> excludedIds,
            Pageable pageable);

    @Query(value = """
            SELECT p FROM Product p JOIN FETCH p.company
            WHERE p.marketplaceId = :marketplaceId
              AND p.status = backend.models.enums.ProductStatus.ACTIVE
              AND p.marketplaceListed = true
              AND p.featured = true
            ORDER BY p.boostWeight DESC NULLS LAST, p.publishedAt DESC
            """,
           countQuery = """
            SELECT COUNT(p) FROM Product p
            WHERE p.marketplaceId = :marketplaceId
              AND p.status = backend.models.enums.ProductStatus.ACTIVE
              AND p.marketplaceListed = true
              AND p.featured = true
            """)
    Page<Product> findFeaturedFeed(
            @Param("marketplaceId") java.util.UUID marketplaceId,
            Pageable pageable);

    /** Used by ProductSimilarityWorker to page through active products per company. */
    @Query("SELECT p FROM Product p WHERE p.company.id = :companyId AND p.status = backend.models.enums.ProductStatus.ACTIVE ORDER BY p.id ASC")
    Page<Product> findActiveByCompanyId(@Param("companyId") java.util.UUID companyId, Pageable pageable);

    /** Returns distinct company IDs that have at least one ACTIVE product — used by nightly similarity worker. */
    @Query("SELECT DISTINCT p.company.id FROM Product p WHERE p.status = backend.models.enums.ProductStatus.ACTIVE")
    List<java.util.UUID> findDistinctCompanyIdsWithActiveProducts();

    /** Featured products by company — last-resort fallback when no similar products are found. */
    @Query("SELECT p FROM Product p WHERE p.company.id = :companyId AND p.featured = true AND p.status = backend.models.enums.ProductStatus.ACTIVE AND p.listed = true ORDER BY p.boostWeight DESC NULLS LAST, p.publishedAt DESC")
    Page<Product> findFeaturedByCompanyId(@Param("companyId") java.util.UUID companyId, Pageable pageable);
}
