import api from "../api";

export interface VendorAnalyticsSummary {
  vendorId: number;
  marketplaceId: number;
  windowDays: number;
  from: string;
  to: string;
  totalSubOrders: number;
  totalGrossRevenue: number;
  totalCommission: number;
  totalNetRevenue: number;
  avgOrderValue: number;
  cancellationRate: number;
  refundRate: number;
  lateShipmentRate: number;
  avgShipHours: number | null;
}

export interface DailyRevenuePoint {
  day: string;
  gross: number;
  commission: number;
  net: number;
  orderCount: number;
}

export interface VendorRevenue {
  vendorId: number;
  windowDays: number;
  from: string;
  to: string;
  totalGross: number;
  totalCommission: number;
  totalNet: number;
  daily: DailyRevenuePoint[];
}

export interface ProductEntry {
  productId: number;
  productName: string;
  totalUnitsSold: number;
  totalRevenue: number;
}

export interface VendorTopProducts {
  vendorId: number;
  windowDays: number;
  products: ProductEntry[];
}

export interface DailyPoint {
  day: string;
  count: number;
  value: number | null;
}

export interface VendorOrdersMetric {
  total: number;
  cancelled: number;
  cancellationRate: number;
  daily: DailyPoint[];
}

export interface VendorRefundsMetric {
  totalReturns: number;
  totalOrders: number;
  refundRate: number;
  daily: DailyPoint[];
}

export interface PayoutSummary {
  payoutId: number;
  netAmount: number;
  currency: string;
  status: string;
  paidAt: string | null;
}

export interface VendorPayoutsMetric {
  vendorId: number;
  totalPaidOut: number;
  totalPayouts: number;
  recent: PayoutSummary[];
}

export interface MarketplaceAnalyticsSummary {
  marketplaceId: number;
  windowDays: number;
  from: string;
  to: string;
  totalOrders: number;
  gmv: number;
  totalCommission: number;
  takeRate: number;
  activeVendors: number;
  ordersDaily: DailyPoint[];
}

export interface TopVendor {
  vendorId: number;
  vendorName: string;
  totalSubOrders: number;
  totalGrossRevenue: number;
  totalCommission: number;
  cancellationRate: number;
}

export const analyticsApi = {
  // Vendor analytics
  getSummary: (marketplaceId: number, vendorId: number, days = 30) =>
    api.get<VendorAnalyticsSummary>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/analytics/summary`,
      { params: { days } }
    ),

  getRevenue: (marketplaceId: number, vendorId: number, days = 30) =>
    api.get<VendorRevenue>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/analytics/revenue`,
      { params: { days } }
    ),

  getTopProducts: (marketplaceId: number, vendorId: number, days = 30, limit = 10) =>
    api.get<VendorTopProducts>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/analytics/top-products`,
      { params: { days, limit } }
    ),

  getOrders: (marketplaceId: number, vendorId: number, days = 30) =>
    api.get<VendorOrdersMetric>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/analytics/orders`,
      { params: { days } }
    ),

  getRefunds: (marketplaceId: number, vendorId: number, days = 30) =>
    api.get<VendorRefundsMetric>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/analytics/refunds`,
      { params: { days } }
    ),

  getPayouts: (marketplaceId: number, vendorId: number, recent = 10) =>
    api.get<VendorPayoutsMetric>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/analytics/payouts`,
      { params: { recent } }
    ),

  // Marketplace operator analytics
  getMarketplaceSummary: (marketplaceId: number, days = 30) =>
    api.get<MarketplaceAnalyticsSummary>(
      `/marketplaces/${marketplaceId}/analytics/summary`,
      { params: { days } }
    ),

  getTopVendors: (marketplaceId: number, days = 30, limit = 10) =>
    api.get<TopVendor[]>(
      `/marketplaces/${marketplaceId}/analytics/top-vendors`,
      { params: { days, limit } }
    ),
};

// ============================================================================
// Company-level product analytics
// ============================================================================

export interface DemandEntry {
  productId: number;
  productName: string;
  sku: string;
  price: number;
  currency: string;
  unitsSold: number;
  revenue: number;
  velocityPerHour: number;
  accelerationRatio: number;
  rank: number;
}

export interface HotProductsResponse {
  window: string;
  computedAt: string;
  windowStart: string;
  products: DemandEntry[];
}

export interface DailyPointOps {
  day: string;
  count: number;
  value: number | null;
}

export interface DurationMetric {
  count: number;
  avgHours: number | null;
  daily: DailyPointOps[];
}

export interface StockoutMetric {
  trackedProducts: number;
  outOfStock: number;
  outOfStockRate: number;
  totalOrders: number;
  ordersWithBackorders: number;
  backorderRate: number;
}

export interface CancellationMetric {
  total: number;
  byReason: { reason: string; count: number }[];
  daily: DailyPointOps[];
}

export interface OperationsSummary {
  companyId: number;
  lookbackDays: number;
  from: string;
  to: string;
  fulfillment: DurationMetric;
  refunds: DurationMetric;
  pickDelays: DurationMetric;
  stockouts: StockoutMetric;
  cancellations: CancellationMetric;
}

export interface ProductForecast {
  productId: number;
  productName: string;
  sku: string;
  currentStock: number | null;
  avgDailyDemand: number;
  predictedWeeklyDemand: number;
  daysOfCoverage: number | null;
  likelyStockoutDate: string | null;
  reorderSuggestedQty: number;
  reorderUrgent: boolean;
}

export interface ForecastSummary {
  companyId: number;
  productCount: number;
  productsNeedingReorder: number;
  productsWithImminentStockout: number;
  items: ProductForecast[];
}

export interface ReorderSuggestion {
  productId: number;
  productName: string;
  sku: string;
  currentStock: number | null;
  suggestedQty: number;
  reasonCode: "BELOW_THRESHOLD" | "STOCKOUT_WITHIN_LEADTIME" | "VELOCITY_SPIKE";
  projectedStockoutDate: string | null;
}

export interface DailyRevPoint {
  day: string;
  totalRevenue: number;
  totalUnits: number;
  orderCount: number;
}

export interface CompanyRevenueSummary {
  companyId: number;
  lookbackDays: number;
  from: string;
  to: string;
  totalRevenue: number;
  totalOrders: number;
  avgOrderValue: number;
  daily: DailyRevPoint[];
}

export interface CategoryEntry {
  category: string;
  totalRevenue: number;
  totalUnits: number;
  orderCount: number;
  revenueSharePercent: number;
}

export interface CategorySalesResponse {
  companyId: number;
  lookbackDays: number;
  categories: CategoryEntry[];
}

export interface SlowMoverEntry {
  productId: number;
  productName: string;
  sku: string;
  currentStock: number | null;
  price: number;
  currency: string;
  unitsSold: number;
  revenue: number;
  dailyVelocity: number;
  neverSold: boolean;
}

export interface SlowMoversResponse {
  companyId: number;
  days: number;
  items: SlowMoverEntry[];
}

export interface ProductPerfEntry {
  productId: number;
  productName: string;
  sku: string;
  currentRevenue: number;
  currentUnits: number;
  priorRevenue: number;
  priorUnits: number;
  revenueGrowthPercent: number;
  unitsGrowthPercent: number;
  revenueRank: number;
  unitsRank: number;
}

export interface ProductPerformanceResponse {
  companyId: number;
  lookbackDays: number;
  computedAt: string;
  products: ProductPerfEntry[];
}

export const companyAnalyticsApi = {
  getHotProducts: (companyId: number, window: "1h" | "24h" = "1h", limit = 20) =>
    api.get<HotProductsResponse>(
      `/companies/${companyId}/analytics/hot-products`,
      { params: { window, limit } }
    ),

  getOperationsSummary: (companyId: number, lookbackDays = 30) =>
    api.get<OperationsSummary>(
      `/companies/${companyId}/operations/summary`,
      { params: { lookbackDays } }
    ),

  getForecastSummary: (companyId: number, lookbackDays = 56, limit = 50) =>
    api.get<ForecastSummary>(
      `/companies/${companyId}/forecasting`,
      { params: { lookbackDays, limit } }
    ),

  getReorderSuggestions: (companyId: number, lookbackDays = 56, limit = 20) =>
    api.get<ReorderSuggestion[]>(
      `/companies/${companyId}/forecasting/reorder-suggestions`,
      { params: { lookbackDays, limit } }
    ),

  getRevenueSummary: (companyId: number, lookbackDays = 30) =>
    api.get<CompanyRevenueSummary>(
      `/companies/${companyId}/analytics/revenue-summary`,
      { params: { lookbackDays } }
    ),

  getCategorySales: (companyId: number, lookbackDays = 30) =>
    api.get<CategorySalesResponse>(
      `/companies/${companyId}/analytics/category-sales`,
      { params: { lookbackDays } }
    ),

  getSlowMovers: (companyId: number, days = 90) =>
    api.get<SlowMoversResponse>(
      `/companies/${companyId}/analytics/slow-movers`,
      { params: { days } }
    ),

  getProductPerformance: (companyId: number, lookbackDays = 30) =>
    api.get<ProductPerformanceResponse>(
      `/companies/${companyId}/analytics/product-performance`,
      { params: { lookbackDays } }
    ),
};
