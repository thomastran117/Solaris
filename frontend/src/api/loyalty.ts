import api from "../api";

export interface LoyaltyAccount {
  id: number;
  userId: number;
  companyId: number;
  pointsBalance: number;
  lifetimePoints: number;
  currentTierId: number | null;
  currentTierName: string | null;
  tierUpdatedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface LoyaltyTransaction {
  id: number;
  accountId: number;
  userId: number;
  companyId: number;
  type:
    | "EARN_ORDER"
    | "EARN_BONUS"
    | "EARN_BIRTHDAY"
    | "REDEEM_ORDER"
    | "CONVERT_TO_CREDIT"
    | "EXPIRE"
    | "ADJUST";
  pointsDelta: number;
  valueCents: number;
  sourceOrderId: number | null;
  expiresAt: string | null;
  reason: string | null;
  createdAt: string;
}

export interface LoyaltyRedemptionQuote {
  userId: number;
  companyId: number;
  pointsToRedeem: number;
  discountCents: number;
  currentBalance: number;
  balanceAfterRedemption: number;
  valid: boolean;
  invalidReason: string | null;
}

export interface LoyaltyPolicy {
  id: number;
  companyId: number;
  name: string;
  earnRatePerDollar: number;
  pointValueCents: number;
  minRedemptionPoints: number;
  pointsExpiryDays: number | null;
  birthdayBonusPoints: number;
  birthdayBonusCreditCents: number;
  cashbackRatePercent: number;
  earnMode: "POINTS" | "CASHBACK" | "BOTH";
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateLoyaltyPolicyRequest {
  name: string;
  earnRatePerDollar?: number;
  pointValueCents?: number;
  minRedemptionPoints?: number;
  pointsExpiryDays?: number | null;
  birthdayBonusPoints?: number;
  birthdayBonusCreditCents?: number;
  cashbackRatePercent?: number;
  earnMode?: "POINTS" | "CASHBACK" | "BOTH";
}

export interface LoyaltyTier {
  id: number;
  companyId: number;
  name: string;
  minPoints: number;
  earnMultiplier: number;
  perksJson: string | null;
  badgeColor: string | null;
  displayOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateLoyaltyTierRequest {
  name: string;
  minPoints: number;
  earnMultiplier?: number;
  perksJson?: string | null;
  badgeColor?: string | null;
  displayOrder?: number;
}

export interface IssueBonusRequest {
  userId: number;
  points: number;
  reason?: string;
}

export interface AdjustPointsRequest {
  pointsDelta: number;
  reason?: string;
}

export const loyaltyApi = {
  // Customer self-service
  getAccount: (companyId: number) =>
    api.get<LoyaltyAccount>(`/loyalty/account`, { params: { companyId } }),

  getTransactions: (companyId: number, page = 0, size = 20) =>
    api.get<LoyaltyTransaction[]>(`/loyalty/transactions`, {
      params: { companyId, page, size },
    }),

  getRedemptionQuote: (companyId: number, points: number) =>
    api.get<LoyaltyRedemptionQuote>(`/loyalty/quote`, {
      params: { companyId, points },
    }),

  // Policy (public read, owner write)
  getPolicy: (companyId: number) =>
    api.get<LoyaltyPolicy>(`/companies/${companyId}/loyalty/policy`),

  createOrUpdatePolicy: (companyId: number, data: CreateLoyaltyPolicyRequest) =>
    api.post<LoyaltyPolicy>(`/companies/${companyId}/loyalty/policy`, data),

  // Tiers (public read, owner write)
  listTiers: (companyId: number) =>
    api.get<LoyaltyTier[]>(`/companies/${companyId}/loyalty/tiers`),

  createTier: (companyId: number, data: CreateLoyaltyTierRequest) =>
    api.post<LoyaltyTier>(`/companies/${companyId}/loyalty/tiers`, data),

  updateTier: (companyId: number, tierId: number, data: CreateLoyaltyTierRequest) =>
    api.put<LoyaltyTier>(`/companies/${companyId}/loyalty/tiers/${tierId}`, data),

  // Operator actions
  issueBonus: (companyId: number, data: IssueBonusRequest) =>
    api.post<LoyaltyTransaction>(`/companies/${companyId}/loyalty/bonus`, data),

  adjustPoints: (companyId: number, accountId: number, data: AdjustPointsRequest) =>
    api.post<LoyaltyTransaction>(
      `/companies/${companyId}/loyalty/accounts/${accountId}/adjust`,
      data
    ),
};
