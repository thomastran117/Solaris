import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { LoyaltyAccount, LoyaltyTier } from "../api/loyalty";

interface LoyaltyState {
  account: LoyaltyAccount | null;
  tiers: LoyaltyTier[];
  isLoading: boolean;
  error: string | null;
}

const initialState: LoyaltyState = {
  account: null,
  tiers: [],
  isLoading: false,
  error: null,
};

const loyaltySlice = createSlice({
  name: "loyalty",
  initialState,
  reducers: {
    setLoyaltyAccount: (state, action: PayloadAction<LoyaltyAccount>) => {
      state.account = action.payload;
      state.error = null;
    },
    setLoyaltyTiers: (state, action: PayloadAction<LoyaltyTier[]>) => {
      state.tiers = action.payload;
    },
    setLoyaltyLoading: (state, action: PayloadAction<boolean>) => {
      state.isLoading = action.payload;
    },
    setLoyaltyError: (state, action: PayloadAction<string | null>) => {
      state.error = action.payload;
      state.isLoading = false;
    },
    clearLoyalty: (state) => {
      state.account = null;
      state.tiers = [];
      state.isLoading = false;
      state.error = null;
    },
  },
});

export const {
  setLoyaltyAccount,
  setLoyaltyTiers,
  setLoyaltyLoading,
  setLoyaltyError,
  clearLoyalty,
} = loyaltySlice.actions;

export default loyaltySlice.reducer;
