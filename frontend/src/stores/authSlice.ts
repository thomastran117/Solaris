import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { UserTier } from "../types/user";

function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    return typeof payload.exp === "number" && payload.exp * 1000 < Date.now();
  } catch {
    return true;
  }
}

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
        const token = action.payload.accessToken;
        // Discard already-expired tokens rather than storing them — prevents stale
        // tokens from being sent until the axios interceptor catches the 401.
        state.accessToken = token && !isTokenExpired(token) ? token : null;
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
