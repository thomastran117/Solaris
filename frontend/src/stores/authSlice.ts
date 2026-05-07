import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

interface AuthState {
  accessToken: string | null;
  email: string | null;
  role: string | null;
  companyId: number | null;
}

const initialState: AuthState = {
  accessToken: null,
  email: null,
  role: null,
  companyId: null,
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
        companyId?: number | null;
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
    },
    clearCredentials: (state) => {
      state.accessToken = null;
      state.email = null;
      state.role = null;
      state.companyId = null;
    },
  },
});

export const { setCredentials, clearCredentials } = authSlice.actions;
export default authSlice.reducer;
