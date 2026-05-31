interface EnvironmentConfig {
  google_client: string;
  ms_client: string;
  msal_authority: string;
  RECAPTCHA_SITE_KEY: string;
  backend_url: string;
  frontend_url: string;
}

const Environment: EnvironmentConfig = {
  google_client: import.meta.env.VITE_GOOGLE_CLIENT_ID ?? "",
  ms_client: import.meta.env.VITE_MSAL_CLIENT_ID ?? "",
  msal_authority: import.meta.env.VITE_MSAL_AUTHORITY ?? "",
  RECAPTCHA_SITE_KEY: import.meta.env.VITE_RECAPTCHA_SITE_KEY ?? "",
  backend_url: import.meta.env.VITE_BACKEND_URL ?? "http://localhost:8090",
  frontend_url: import.meta.env.VITE_FRONTEND_URL ?? "http://localhost:3090",
};

if (!Environment.google_client) {
  console.warn("Missing VITE_GOOGLE_CLIENT_ID in .env file");
}

if (!Environment.ms_client) {
  console.warn("Missing VITE_MSAL_CLIENT_ID in .env file");
}

export default Environment;
