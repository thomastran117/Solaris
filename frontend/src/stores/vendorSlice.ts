import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

export interface MarketplaceVendor {
  id: string;
  marketplaceId: string;
  marketplaceName: string;
  vendorCompanyId: string;
  vendorCompanyName: string;
  status: string;
  tier: string;
  onboardingStep: string;
  commissionPolicyId: string | null;
  stripeConnectStatus: string | null;
  chargesEnabled: boolean;
  payoutsEnabled: boolean;
  appliedAt: string | null;
  approvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

interface VendorState {
  currentVendor: MarketplaceVendor | null;
  isLoading: boolean;
  error: string | null;
}

const initialState: VendorState = {
  currentVendor: null,
  isLoading: false,
  error: null,
};

const vendorSlice = createSlice({
  name: "vendor",
  initialState,
  reducers: {
    setCurrentVendor: (state, action: PayloadAction<MarketplaceVendor | null>) => {
      state.currentVendor = action.payload;
      state.error = null;
    },
    setVendorLoading: (state, action: PayloadAction<boolean>) => {
      state.isLoading = action.payload;
    },
    setVendorError: (state, action: PayloadAction<string | null>) => {
      state.error = action.payload;
      state.isLoading = false;
    },
    clearVendor: (state) => {
      state.currentVendor = null;
      state.isLoading = false;
      state.error = null;
    },
  },
});

export const { setCurrentVendor, setVendorLoading, setVendorError, clearVendor } =
  vendorSlice.actions;
export default vendorSlice.reducer;
