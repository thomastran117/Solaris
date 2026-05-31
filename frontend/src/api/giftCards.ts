import api from "../api";
import type { GiftCard, GiftCardBalance, PagedGiftCards } from "../types/giftCard";

export const giftCardsApi = {
  redeem: (code: string, amountCents: number) =>
    api.post<GiftCard>("/gift-cards/redeem", { code, amountCents }),

  getBalance: (code: string) =>
    api.get<GiftCardBalance>(`/gift-cards/${encodeURIComponent(code)}/balance`),

  list: (params?: { page?: number; size?: number }) =>
    api.get<PagedGiftCards>("/gift-cards", { params }),
};
