import { NextResponse, type NextRequest } from "next/server";
import { isProtectedPath, loginUrlFor, REFRESH_COOKIE } from "./lib/routes";

/**
 * Optimistic auth check for protected routes (ADR 0001, "Auth boundary").
 *
 * When the `refreshToken` cookie is absent the visitor cannot have a session, so we
 * redirect to /login?next=<original path and query> before sending the protected
 * shell. When the cookie is present we let the request through: the token is
 * single-use and only the browser may redeem it, so this never calls the backend or
 * inspects the cookie. `RequireAuth` makes the authoritative client-side decision
 * and the backend authorizes every API call.
 *
 * Because the cookie is SameSite=Strict, a top-level navigation from another site
 * (Stripe return, email link) arrives without it and is sent to /login; the login
 * page then restores the session with a same-site request and returns to `next`.
 */
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  if (!isProtectedPath(pathname)) return NextResponse.next();
  if (request.cookies.has(REFRESH_COOKIE)) return NextResponse.next();

  const target = new URL(loginUrlFor(`${pathname}${search}`), request.url);
  return NextResponse.redirect(target, 307);
}

// Must be static literals (analysed at build time); keep in sync with
// PROTECTED_PREFIXES in src/lib/routes.ts. isProtectedPath() refines the match,
// e.g. to let /lists/public/* through.
export const config = {
  matcher: [
    "/account/:path*",
    "/admin/:path*",
    "/b2b/:path*",
    "/checkout/:path*",
    "/dashboard/:path*",
    "/feed/:path*",
    "/feedback/:path*",
    "/following/:path*",
    "/lists/:path*",
    "/loyalty/:path*",
    "/orders/:path*",
    "/price-watches/:path*",
  ],
};
