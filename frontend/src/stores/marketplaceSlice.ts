import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

export interface MarketplaceProfile {
  id: string;
  companyId: string;
  companyName: string;
  slug: string;
  defaultCommissionPolicyId: string | null;
  payoutSchedule: string;
  holdPeriodDays: number;
  defaultCurrency: string;
  acceptingApplications: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface MarketplaceState {
  currentMarketplace: MarketplaceProfile | null;
  /**
   * Storefront marketplace configured by `DEFAULT_MARKETPLACE_ID`. Seeded once by
   * the root layout so server-rendered and client queries use the same ID.
   */
  defaultMarketplaceId: string | null;
  isLoading: boolean;
  error: string | null;
}

const initialState: MarketplaceState = {
  currentMarketplace: null,
  defaultMarketplaceId: null,
  isLoading: false,
  error: null,
};

/** The marketplace catalog queries should target: an explicit selection, else the configured default. */
export const selectActiveMarketplaceId = (state: {
  marketplace: MarketplaceState;
}): string | null =>
  state.marketplace.currentMarketplace?.id ??
  state.marketplace.defaultMarketplaceId ??
  null;

/** Initial marketplace state with the configured default applied. */
export function marketplaceStateWithDefault(
  defaultMarketplaceId: string | null
): MarketplaceState {
  return { ...initialState, defaultMarketplaceId };
}

const marketplaceSlice = createSlice({
  name: "marketplace",
  initialState,
  reducers: {
    setCurrentMarketplace: (state, action: PayloadAction<MarketplaceProfile | null>) => {
      state.currentMarketplace = action.payload;
      state.error = null;
    },
    setMarketplaceLoading: (state, action: PayloadAction<boolean>) => {
      state.isLoading = action.payload;
    },
    setMarketplaceError: (state, action: PayloadAction<string | null>) => {
      state.error = action.payload;
      state.isLoading = false;
    },
    // Clears the explicit selection; the configured default stays in effect.
    clearMarketplace: (state) => {
      state.currentMarketplace = null;
      state.isLoading = false;
      state.error = null;
    },
  },
});

export const {
  setCurrentMarketplace,
  setMarketplaceLoading,
  setMarketplaceError,
  clearMarketplace,
} = marketplaceSlice.actions;
export default marketplaceSlice.reducer;
