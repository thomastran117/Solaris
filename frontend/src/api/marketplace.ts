import api from "../api";
import type { MarketplaceProfile } from "../stores/marketplaceSlice";

export interface CreateMarketplaceRequest {
  slug: string;
  payoutSchedule?: "WEEKLY" | "BIWEEKLY" | "MONTHLY";
  holdPeriodDays?: number;
  defaultCurrency?: string;
  acceptingApplications?: boolean;
}

export interface UpdateMarketplaceRequest {
  payoutSchedule?: "WEEKLY" | "BIWEEKLY" | "MONTHLY";
  holdPeriodDays?: number;
  acceptingApplications?: boolean;
}

export const marketplaceApi = {
  create: (companyId: number, data: CreateMarketplaceRequest) =>
    api.post<MarketplaceProfile>(`/marketplaces/companies/${companyId}`, data),

  get: (marketplaceId: number) =>
    api.get<MarketplaceProfile>(`/marketplaces/${marketplaceId}`),

  update: (marketplaceId: number, data: UpdateMarketplaceRequest) =>
    api.patch<MarketplaceProfile>(`/marketplaces/${marketplaceId}`, data),
};
