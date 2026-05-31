export type GiftCardStatus = "ACTIVE" | "PARTIALLY_USED" | "REDEEMED" | "VOID";

export interface GiftCard {
  id: string;
  code: string;
  companyId: string;
  originalValueCents: number;
  remainingBalanceCents: number;
  purchasedByUserId: string;
  purchasedOnOrderId: string | null;
  status: GiftCardStatus;
  redeemedAt: string | null;
  createdAt: string;
}

export interface GiftCardBalance {
  code: string;
  remainingBalanceCents: number;
  status: GiftCardStatus;
}

export interface PagedGiftCards {
  content: GiftCard[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}
