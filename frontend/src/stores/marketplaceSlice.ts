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

interface MarketplaceState {
  currentMarketplace: MarketplaceProfile | null;
  isLoading: boolean;
  error: string | null;
}

const initialState: MarketplaceState = {
  currentMarketplace: null,
  isLoading: false,
  error: null,
};

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
