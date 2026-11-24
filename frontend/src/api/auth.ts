import api from "../api";
import type { UserTier } from "../types/user";

/** `AuthResponse`: a signed-in session. The refresh cookie is set alongside. */
export interface AuthSession {
  token: string;
  email: string;
  usertype: string;
  userid?: string;
  tier: UserTier;
}

/** Sent instead of a session when the login comes from an unrecognised device. */
export interface DeviceVerificationRequired {
  status: "DEVICE_VERIFICATION_REQUIRED";
}

export type LoginResponse = AuthSession | DeviceVerificationRequired;

export interface Credentials {
  email: string;
  password: string;
  /** reCAPTCHA token; the backend rejects the request without one. */
  captcha: string;
}

export function needsDeviceVerification(
  response: LoginResponse
): response is DeviceVerificationRequired {
  return (
    (response as DeviceVerificationRequired).status ===
    "DEVICE_VERIFICATION_REQUIRED"
  );
}

export const authApi = {
  login: (body: Credentials) => api.post<LoginResponse>("/auth/login", body),

  google: (idToken: string) =>
    api.post<LoginResponse>("/auth/google", { idToken }),

  /** Creates the account and emails a verification link (201). */
  signup: (body: Credentials) =>
    api.post<{ message: string }>("/auth/signup", body),

  /** Consumes the single-use token from the `/verify-email` link. */
  verifyEmail: (token: string) =>
    api.post<{ message: string }>("/auth/verify", null, { params: { token } }),

  /** Consumes the `/verify-device` link token and signs the device in. */
  verifyDevice: (token: string) =>
    api.post<LoginResponse>("/auth/verify-device", null, {
      params: { token },
    }),
};
