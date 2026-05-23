import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { UserTier } from "../types/user";

interface AuthState {
  accessToken: string | null;
  email: string | null;
  role: string | null;
  companyId: string | null;
  tier: UserTier | null;
}

const initialState: AuthState = {
  accessToken: null,
  email: null,
  role: null,
  companyId: null,
  tier: null,
};

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    setCredentials: (
      state,
      action: PayloadAction<{
        accessToken?: string | null;
        email?: string | null;
        role?: string | null;
        companyId?: string | null;
        tier?: UserTier | null;
      }>
    ) => {
      if (action.payload.accessToken !== undefined) {
        state.accessToken = action.payload.accessToken;
      }
      if (action.payload.email !== undefined) {
        state.email = action.payload.email;
      }
      if (action.payload.role !== undefined) {
        state.role = action.payload.role;
      }
      if (action.payload.companyId !== undefined) {
        state.companyId = action.payload.companyId;
      }
      if (action.payload.tier !== undefined) {
        state.tier = action.payload.tier;
      }
    },
    clearCredentials: (state) => {
      state.accessToken = null;
      state.email = null;
      state.role = null;
      state.companyId = null;
      state.tier = null;
    },
  },
});

export const { setCredentials, clearCredentials } = authSlice.actions;
export default authSlice.reducer;
