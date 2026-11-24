/**
 * Single source of truth for which URLs need a signed-in session. Used by
 * `src/proxy.ts` (server-side redirect hint), `RequireAuth` (client guard), and tests.
 * The backend still authorizes every API call; this only decides where to send
 * visitors who are clearly not signed in.
 */
export const PROTECTED_PREFIXES = [
  "/account",
  "/admin",
  "/b2b",
  "/checkout",
  "/dashboard",
  "/feed",
  "/feedback",
  "/following",
  "/lists",
  "/loyalty",
  "/orders",
  "/price-watches",
] as const;

/** Shared saved lists are public even though they live under /lists. */
const PUBLIC_EXCEPTIONS = ["/lists/public/"] as const;

export function isProtectedPath(pathname: string): boolean {
  if (PUBLIC_EXCEPTIONS.some((p) => pathname.startsWith(p))) return false;
  return PROTECTED_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`)
  );
}

/** Name of the httpOnly refresh-token cookie set by the backend (`AuthController`). */
export const REFRESH_COOKIE = "refreshToken";

/** Where to go after signing in when no valid `next` was requested. */
export const DEFAULT_AFTER_LOGIN = "/";

/** Builds the login URL that returns the visitor to `pathAndQuery` afterwards. */
export function loginUrlFor(pathAndQuery: string): string {
  return `/login?next=${encodeURIComponent(pathAndQuery)}`;
}
