import axios from "axios";
import Environment from "../configuration/Environment";
import { clearCredentials, setCredentials } from "../stores/authSlice";
import type { UserTier } from "../types/user";
import { getBoundStore } from "./authBridge";
import { envelopeData } from "./envelope";

interface RefreshPayload {
  accessToken?: string;
  email?: string | null;
  role?: string | null;
  tier?: UserTier | null;
}

let inFlight: Promise<string> | null = null;

/**
 * Exchanges the `refreshToken` cookie for a new access token and stores the result.
 *
 * Refresh tokens are single-use: the backend consumes the presented token and sets a
 * replacement cookie. Two overlapping refresh requests would therefore race, and the
 * loser would fail and log the user out. Every caller (session restore on load, the
 * 401 retry interceptor, React StrictMode's double effects) shares one in-flight
 * request through this function.
 *
 * Resolves with the new access token; rejects (after clearing credentials) when the
 * session cannot be restored.
 */
export function refreshSession(): Promise<string> {
  if (inFlight) return inFlight;

  inFlight = axios
    .post(
      `${Environment.backend_url}/auth/refresh`,
      {},
      { withCredentials: true }
    )
    .then((resp) => {
      const payload = envelopeData<RefreshPayload | null>(resp.data);
      const accessToken = payload?.accessToken;
      if (!accessToken)
        throw new Error("Refresh response did not include a token");
      getBoundStore()?.dispatch(
        setCredentials({
          accessToken,
          email: payload?.email ?? null,
          role: payload?.role ?? null,
          tier: payload?.tier ?? null,
        })
      );
      return accessToken;
    })
    .catch((err: unknown) => {
      getBoundStore()?.dispatch(clearCredentials());
      throw err;
    })
    .finally(() => {
      inFlight = null;
    });

  return inFlight;
}
