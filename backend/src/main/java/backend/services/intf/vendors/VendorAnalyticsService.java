package backend.services.intf.vendors;

import java.util.UUID;
import backend.dtos.responses.marketplace.MarketplaceAnalyticsSummaryResponse;
import backend.dtos.responses.marketplace.TopVendorResponse;
import backend.dtos.responses.vendor.VendorAnalyticsSummaryResponse;
import backend.dtos.responses.vendor.VendorOrdersMetricResponse;
import backend.dtos.responses.vendor.VendorPayoutsMetricResponse;
import backend.dtos.responses.vendor.VendorRefundsMetricResponse;
import backend.dtos.responses.vendor.VendorRevenueResponse;
import backend.dtos.responses.vendor.VendorTopProductsResponse;

import java.util.List;

public interface VendorAnalyticsService {

    // Vendor-scoped endpoints
    VendorAnalyticsSummaryResponse getSummary(UUID vendorId, UUID marketplaceId, int lookbackDays, UUID actorUserId);
    VendorRevenueResponse getRevenue(UUID vendorId, UUID marketplaceId, int lookbackDays, UUID actorUserId);
    VendorTopProductsResponse getTopProducts(UUID vendorId, UUID marketplaceId, int lookbackDays, int limit, UUID actorUserId);
    VendorOrdersMetricResponse getOrders(UUID vendorId, UUID marketplaceId, int lookbackDays, UUID actorUserId);
    VendorRefundsMetricResponse getRefunds(UUID vendorId, UUID marketplaceId, int lookbackDays, UUID actorUserId);
    VendorPayoutsMetricResponse getPayouts(UUID vendorId, UUID marketplaceId, int recentCount, UUID actorUserId);

    // Marketplace operator endpoints
    MarketplaceAnalyticsSummaryResponse getMarketplaceSummary(UUID marketplaceId, UUID operatorUserId, int lookbackDays);
    List<TopVendorResponse> getTopVendors(UUID marketplaceId, UUID operatorUserId, int lookbackDays, int limit);
}
