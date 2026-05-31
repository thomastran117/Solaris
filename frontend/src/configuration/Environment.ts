interface EnvironmentConfig {
  google_client: string;
  ms_client: string;
  msal_authority: string;
  RECAPTCHA_SITE_KEY: string;
  backend_url: string;
  backend_url_ws: string;
  frontend_url: string;
}

const normalizeUrl = (value: string) => value.replace(/\/+$/, "");
const readEnv = (value: string | undefined, fallback = "") =>
  value && value.trim() ? value.trim() : fallback;

const defaultFrontendUrl =
  typeof window !== "undefined" ? window.location.origin : "http://localhost:3090";

const Environment: EnvironmentConfig = {
  google_client: readEnv(import.meta.env.VITE_GOOGLE_CLIENT_ID),
  ms_client: readEnv(import.meta.env.VITE_MSAL_CLIENT_ID),
  msal_authority: readEnv(import.meta.env.VITE_MSAL_AUTHORITY),
  RECAPTCHA_SITE_KEY: readEnv(import.meta.env.VITE_RECAPTCHA_SITE_KEY),
  backend_url: normalizeUrl(readEnv(import.meta.env.VITE_BACKEND_URL, "/api")),
  backend_url_ws: readEnv(
    import.meta.env.VITE_BACKEND_WS_URL,
    typeof window !== "undefined"
      ? window.location.origin.replace(/^http/, "ws")
      : "ws://localhost:8080"
  ),
  frontend_url: normalizeUrl(
    readEnv(import.meta.env.VITE_FRONTEND_URL, defaultFrontendUrl)
  ),
};

if (!Environment.google_client) {
  console.warn("Missing VITE_GOOGLE_CLIENT_ID in .env file");
}

if (!Environment.ms_client) {
  console.warn("Missing VITE_MSAL_CLIENT_ID in .env file");
}

export default Environment;
