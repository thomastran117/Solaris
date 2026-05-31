export type UserTier = 'FREE' | 'PREMIUM';

export interface PremiumStatus {
  tier: UserTier;
  premiumExpiresAt: string | null;
}
