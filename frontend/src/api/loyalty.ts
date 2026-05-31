import api from "../api";

export interface LoyaltyAccount {
  id: string;
  userId: string;
  companyId: string;
  pointsBalance: number;
  lifetimePoints: number;
  currentTierId: string | null;
  currentTierName: string | null;
  tierUpdatedAt: string | null;
  referralCode: string | null;
  currentStreak: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface LoyaltyExpiryWarning {
  pointsExpiringSoon: number;
  nextExpiryAt: string | null;
}

export interface LoyaltyReferral {
  referralCode: string;
  totalReferrals: number;
  convertedReferrals: number;
  pointsEarnedFromReferrals: number;
}

export interface LoyaltyTransaction {
  id: string;
  accountId: string;
  userId: string;
  companyId: string;
  type:
    | "EARN_ORDER"
    | "EARN_BONUS"
    | "EARN_BIRTHDAY"
    | "EARN_REFERRAL"
    | "EARN_STREAK"
    | "REDEEM_ORDER"
    | "RESTORE_ORDER"
    | "EARN_REVERSAL"
    | "CONVERT_TO_CREDIT"
    | "EXPIRE"
    | "ADJUST";
  pointsDelta: number;
  valueCents: number;
  sourceOrderId: string | null;
  expiresAt: string | null;
  reason: string | null;
  createdAt: string;
}

export interface LoyaltyRedemptionQuote {
  userId: string;
  companyId: string;
  pointsToRedeem: number;
  discountCents: number;
  currentBalance: number;
  balanceAfterRedemption: number;
  valid: boolean;
  invalidReason: string | null;
}

export interface LoyaltyPolicy {
  id: string;
  companyId: string;
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
  referralBonusPoints: number;
  streakBonusThreshold: number;
  streakBonusPoints: number;
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
  referralBonusPoints?: number;
  streakBonusThreshold?: number;
  streakBonusPoints?: number;
}

export interface LoyaltyTier {
  id: string;
  companyId: string;
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
  userId: string;
  points: number;
  reason?: string;
}

export interface AdjustPointsRequest {
  pointsDelta: number;
  reason?: string;
}

export const loyaltyApi = {
  // Customer self-service
  getAccount: (companyId: string) =>
    api.get<LoyaltyAccount>(`/loyalty/account`, { params: { companyId } }),

  getTransactions: (companyId: string, page = 0, size = 20) =>
    api.get<LoyaltyTransaction[]>(`/loyalty/transactions`, {
      params: { companyId, page, size },
    }),

  getRedemptionQuote: (companyId: string, points: number) =>
    api.get<LoyaltyRedemptionQuote>(`/loyalty/quote`, {
      params: { companyId, points },
    }),

  // Policy (public read, owner write)
  getPolicy: (companyId: string) =>
    api.get<LoyaltyPolicy>(`/companies/${companyId}/loyalty/policy`),

  createOrUpdatePolicy: (companyId: string, data: CreateLoyaltyPolicyRequest) =>
    api.post<LoyaltyPolicy>(`/companies/${companyId}/loyalty/policy`, data),

  // Tiers (public read, owner write)
  listTiers: (companyId: string) =>
    api.get<LoyaltyTier[]>(`/companies/${companyId}/loyalty/tiers`),

  createTier: (companyId: string, data: CreateLoyaltyTierRequest) =>
    api.post<LoyaltyTier>(`/companies/${companyId}/loyalty/tiers`, data),

  updateTier: (companyId: string, tierId: string, data: CreateLoyaltyTierRequest) =>
    api.put<LoyaltyTier>(`/companies/${companyId}/loyalty/tiers/${tierId}`, data),

  // Expiry warning & referral
  getExpiryWarning: (companyId: string, days = 30) =>
    api.get<LoyaltyExpiryWarning>(`/loyalty/expiry-warning`, { params: { companyId, days } }),

  getReferralInfo: (companyId: string) =>
    api.get<LoyaltyReferral>(`/loyalty/referral`, { params: { companyId } }),

  applyReferralCode: (companyId: string, code: string) =>
    api.post<void>(`/loyalty/referral/apply`, { code }, { params: { companyId } }),

  // Operator actions
  issueBonus: (companyId: string, data: IssueBonusRequest) =>
    api.post<LoyaltyTransaction>(`/companies/${companyId}/loyalty/bonus`, data),

  adjustPoints: (companyId: string, accountId: string, data: AdjustPointsRequest) =>
    api.post<LoyaltyTransaction>(
      `/companies/${companyId}/loyalty/accounts/${accountId}/adjust`,
      data
    ),
};
