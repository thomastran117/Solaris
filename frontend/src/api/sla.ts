import api from "../api";

export interface VendorSLAPolicy {
  id: number;
  marketplaceId: number;
  name: string;
  targetShipHours: number;
  targetResponseHours: number;
  maxCancellationRate: number;
  maxRefundRate: number;
  maxLateShipmentRate: number;
  breachAction: "WARN" | "RESTRICT_LISTINGS" | "SUSPEND";
  evaluationWindowDays: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSLAPolicyRequest {
  name: string;
  targetShipHours?: number;
  targetResponseHours?: number;
  maxCancellationRate?: number;
  maxRefundRate?: number;
  maxLateShipmentRate?: number;
  breachAction: "WARN" | "RESTRICT_LISTINGS" | "SUSPEND";
  evaluationWindowDays?: number;
}

export interface VendorSLAMetric {
  id: number;
  vendorId: number;
  marketplaceId: number;
  date: string;
  totalOrders: number;
  shipHoursP50: number | null;
  shipHoursP90: number | null;
  cancellationRate: number;
  refundRate: number;
  lateShipmentRate: number;
  defectRate: number;
  createdAt: string;
}

export interface VendorSLABreach {
  id: number;
  vendorId: number;
  policyId: number;
  metric: string;
  actualValue: number;
  threshold: number;
  detectedAt: string;
  resolvedAt: string | null;
  actionTaken: "WARN" | "RESTRICT_LISTINGS" | "SUSPEND";
  notificationSentAt: string | null;
  createdAt: string;
}

export const slaApi = {
  // Policy management (operator)
  createPolicy: (marketplaceId: number, data: CreateSLAPolicyRequest) =>
    api.post<VendorSLAPolicy>(`/marketplaces/${marketplaceId}/sla/policies`, data),

  listPolicies: (marketplaceId: number) =>
    api.get<VendorSLAPolicy[]>(`/marketplaces/${marketplaceId}/sla/policies`),

  getActivePolicy: (marketplaceId: number) =>
    api.get<VendorSLAPolicy>(`/marketplaces/${marketplaceId}/sla/policies/active`),

  // Metrics (vendor + operator)
  listMetrics: (marketplaceId: number, vendorId: number, page = 0, size = 30) =>
    api.get<VendorSLAMetric[]>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/sla/metrics`,
      { params: { page, size } }
    ),

  getLatestMetric: (marketplaceId: number, vendorId: number) =>
    api.get<VendorSLAMetric>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/sla/metrics/latest`
    ),

  // Breaches (vendor + operator)
  listBreaches: (marketplaceId: number, vendorId: number, page = 0, size = 20) =>
    api.get<VendorSLABreach[]>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/sla/breaches`,
      { params: { page, size } }
    ),

  resolveBreach: (marketplaceId: number, breachId: number) =>
    api.post<VendorSLABreach>(
      `/marketplaces/${marketplaceId}/sla/breaches/${breachId}/resolve`
    ),
};
