/**
 * Browser-visible configuration. Next.js inlines `NEXT_PUBLIC_*` values into both the
 * server and client bundles at build time, so every value here is identical on both
 * sides and safe to read during render. Values derived from `window` are exposed as
 * functions and must only be called from event handlers or effects.
 *
 * Never put secrets in `NEXT_PUBLIC_*` variables. Server-only settings live in
 * `configuration/server.ts`.
 */
interface EnvironmentConfig {
  google_client: string;
  ms_client: string;
  msal_authority: string;
  RECAPTCHA_SITE_KEY: string;
  backend_url: string;
}

const normalizeUrl = (value: string) => value.replace(/\/+$/, "");
const readEnv = (value: string | undefined, fallback = "") =>
  value && value.trim() ? value.trim() : fallback;

// Each variable is referenced literally so Next.js can inline it.
const configuredFrontendUrl = normalizeUrl(
  readEnv(process.env.NEXT_PUBLIC_FRONTEND_URL)
);
const configuredBackendWsUrl = normalizeUrl(
  readEnv(process.env.NEXT_PUBLIC_BACKEND_WS_URL)
);

const Environment: EnvironmentConfig = {
  google_client: readEnv(process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID),
  ms_client: readEnv(process.env.NEXT_PUBLIC_MSAL_CLIENT_ID),
  msal_authority: readEnv(process.env.NEXT_PUBLIC_MSAL_AUTHORITY),
  RECAPTCHA_SITE_KEY: readEnv(process.env.NEXT_PUBLIC_RECAPTCHA_SITE_KEY),
  backend_url: normalizeUrl(
    readEnv(process.env.NEXT_PUBLIC_BACKEND_URL, "/api")
  ),
};

/** Public origin of this frontend, e.g. for OAuth redirect URIs. Browser only. */
export function getFrontendUrl(): string {
  return configuredFrontendUrl || window.location.origin;
}

/**
 * Base URL for STOMP/WebSocket connections, including the backend's `/api` context
 * path. Defaults to the current origin (proxied like `/api`). Browser only.
 */
export function getBackendWsUrl(): string {
  if (configuredBackendWsUrl) return configuredBackendWsUrl;
  const origin = window.location.origin.replace(/^http/, "ws");
  const base = Environment.backend_url;
  return /^https?:\/\//.test(base)
    ? base.replace(/^http/, "ws")
    : `${origin}${base}`;
}

export default Environment;
