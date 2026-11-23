# 0001. Migrate the Frontend from a Vite SPA to Next.js App Router

- Status: Accepted
- Date: 2026-11-22
- Tracking: epic #66; child issues #82 (this record), #83, #84, #85, #86
- Route inventory: [frontend route map](../frontend-route-map.md)

## Context

The frontend in `frontend/` is a React 19 single-page application built by Vite 6 and routed by `react-router-dom` 7. In production, Nginx serves the static bundle with an `index.html` fallback and proxies `/api/` to the Spring Boot backend (`frontend/nginx.conf`). Every page renders client-side after JavaScript loads.

The current shell has these consequences and constraints:

- **Public pages are not indexable or shareable.** Products, merchants, collections, kits, and public saved lists return an empty `#root` until JavaScript runs and TanStack Query fetches data. Link previews and search crawlers see no content or metadata.
- **There is no 404 page.** `frontend/src/App.tsx` declares 53 flat routes with no catch-all, so unknown URLs render an empty body between the Navbar and Footer and respond `200`.
- **The catalog is not wired to a marketplace.** Home featured collections, Browse, Product, Collection, and Compare require `state.marketplace.currentMarketplace`, but nothing dispatches `setCurrentMarketplace` (`frontend/src/stores/marketplaceSlice.ts`). Those queries are disabled in a real browser. The backend has no "default marketplace" lookup.
- **Session state is client-only.** The access token lives only in Redux memory (`frontend/src/stores/authSlice.ts`). On every load `AuthInitializer` calls `POST /auth/refresh` using the `refreshToken` cookie, then shows a full-screen spinner until it finishes. The cookie is `HttpOnly`, host-only, `Path=/`, `SameSite` from `COOKIE_SAME_SITE` (default `Strict`), and **single-use**: refresh consumes it and issues a replacement (`AuthController#buildRefreshCookie`, `TokenServiceImpl`). CSRF protection is disabled on the basis of `SameSite=Strict` (`RestSecurityConfiguration`).
- **Route guarding is presence-only.** `ProtectedRoute` checks that an access token exists, redirects to `/login` without remembering the destination, and performs no role check. Capability and role gating lives in the UI (`useCompanyCapabilities`, Navbar), and the backend enforces authorization.
- **Module singletons.** The Redux store and `QueryClient` are created at module scope and imported directly by `src/api.ts`, `hooks/useOrderStream.ts`, and `hooks/useDriverWebSocket.ts`. That is safe in a browser but would share state between requests on a server.
- **Real-time paths.** Order tracking uses SSE (`@microsoft/fetch-event-source`) at `/api/orders/{id}/stream`; driver dispatch uses STOMP over WebSocket at `/api/ws/delivery`. The current WebSocket fallback URL omits the `/api` context path, and `nginx.conf` has no upgrade headers.

## Decision

Replace the Vite/React Router shell with **Next.js App Router** (Next.js 16, React 19.2, TypeScript, Tailwind CSS 4, npm). The Spring Boot API, its contracts, and its security configuration stay unchanged.

### Rendering policy

| Route class | Mode | Data |
|---|---|---|
| Public storefront: `/`, `/browse`, `/products/[id]`, `/c/[id]`, `/collections/[slug]`, `/kits/[companyId]/[kitId]`, `/lists/public/[slug]` | Server-rendered; ISR (`revalidate = 60`) except `/browse`, which renders per request because it is driven by query parameters | Public, anonymous backend reads prefetched on the server and handed to TanStack Query through `HydrationBoundary` using the same query keys as the client |
| Static public: `/terms-and-conditions`, `/privacy` | Static | None |
| Public but interactive: `/c/[id]/compare`, `/gift-cards`, `/login`, `/invite/accept/[token]`, `/auth/google` | Server-rendered shell with client data | Client-only |
| Customer account and merchant/staff (`/admin/*`) | Client-rendered behind an auth boundary | Client-only, `Authorization: Bearer` from memory |

Rules that follow from the policy:

- **Server fetches are anonymous.** The server data helper never forwards cookies or `Authorization`, so server HTML and the Next.js data cache can only contain public data. Per-user state (follow status, back-in-stock subscriptions, price watches, loyalty balance) is fetched in the browser after hydration.
- **Server-rendered storefront routes do not stream a `loading.tsx` fallback.** They await their prefetch so that HTML without JavaScript contains the content and `notFound()` returns HTTP `404`. A backend `5xx` or timeout renders the page without prefetched data, and the client retries.
- **Entrance animations must not hide server HTML.** Framer Motion `initial` states are disabled for server-rendered and hydrated content and kept for content mounted after client navigation, always respecting `useReducedMotion`.
- **Metadata.** Storefront routes define `generateMetadata` (title, description, Open Graph, canonical URL). `/browse` is `noindex` when free-text or price filters are present. Public saved lists, account routes, and merchant routes are `noindex`. `robots.txt` disallows protected prefixes.

### Auth boundary

- **No server-side refresh, ever.** Refresh tokens are single-use, so a server-side refresh would race the browser and log users out. Session restoration stays in the browser through one shared in-flight `refreshSession()` used by both `AuthInitializer` and the 401 interceptor.
- **`proxy.ts` is a hint, not an authority.** For protected path prefixes it redirects (`307`) to `/login?next=<path+query>` when the `refreshToken` cookie is absent. It never inspects or validates the cookie.
- **The client guard is authoritative in the frontend.** Account and merchant layouts render a spinner until the session status is known, then redirect anonymous users to `/login?next=…`. Protected HTML shells contain no private data.
- **The backend remains the authority** for authorization, roles, membership, and tenant scope. Frontend role checks only hide navigation.
- **`next` is validated** as a same-origin relative path; `//`, backslash, control characters, cross-origin resolution, and `/login`/`/auth/*` loops are rejected.
- **`SameSite=Strict` stays.** A top-level navigation from another site (Stripe Checkout return to `/account`, an email link) arrives without the cookie, so the proxy sends the user to `/login?next=…`. The login page's same-site refresh then succeeds and it returns the user to `next` without asking for credentials. Relaxing `SameSite` would require re-enabling CSRF protection and is out of scope.

### Marketplace context

A configured default marketplace ID resolves the storefront marketplace while keeping URLs unchanged. `DEFAULT_MARKETPLACE_ID` is read on the server (with `NEXT_PUBLIC_DEFAULT_MARKETPLACE_ID` as a fallback), passed from the root layout into the Redux store, and used by both server prefetch and client queries so that their query keys match. When it is unset, catalog pages render a "marketplace not configured" state instead of failing silently.

### Configuration and deployment

- Browser-visible variables move from `VITE_*` to `NEXT_PUBLIC_*` with the same meaning. They are inlined at build time.
- The browser keeps calling same-origin `/api/*`. Next.js rewrites forward it to `API_INTERNAL_URL` (`http://localhost:8090` in development, `http://backend:8090` in Compose). Same-origin calls keep the host-only refresh cookie and avoid CORS. Rewrites are fixed at build time in standalone output, so `API_INTERNAL_URL` is a Docker build argument as well as a runtime variable for server fetches.
- Production runs `next build` with `output: "standalone"` as a non-root Node server on port 3000, with a `/healthz` route handler (outside `/api`, which is rewritten). Nginx and its SPA fallback are removed.
- Local development keeps port 3090 so existing `CORS_ALLOWED_ORIGINS` defaults and the Google OAuth redirect URI (`<frontend>/auth/google`) remain valid.

### Code organization

- The App Router tree lives in `frontend/src/app` and uses route groups: `(storefront)`, `(auth)`, `(account)`, and `(merchant)`. URL paths do not change.
- `frontend/src/pages` is renamed to `frontend/src/views`, because Next.js would otherwise treat it as a Pages Router directory, including test files. Each `page.tsx` is a thin server component that renders an existing view.
- React Router APIs are replaced with `next/link` and `next/navigation`, plus two small helpers that keep call sites close to today's: `NavLink` (active-state class callback) and `useSetSearchParams` (replace or push without scrolling).
- The store becomes `makeStore()` and the query client becomes `makeQueryClient()`/`getQueryClient()`. Non-React code reaches the access token through a small bridge registered by the client store provider, not a shared module singleton.
- Vitest, Testing Library, and jsdom remain the unit test stack. `vite` stays only as Vitest's dependency.

## Alternatives Considered

- **React Router 7 framework mode.** It provides SSR with less code churn, but the team chose Next.js for its built-in metadata API, ISR/data cache, standalone output, and the broader ecosystem the epic targets.
- **Vite SSR or prerendering.** Prerendering cannot cover dynamic product IDs. A hand-built Vite SSR server would recreate routing, data loading, caching, and streaming that Next.js provides.
- **Stay a SPA and add a prerender or meta-tag service.** That fixes link previews only partially, adds an extra runtime, and leaves the no-404 and indexing gaps.
- **Marketplace in the URL (`/m/[marketplaceId]/…`) or by host name.** Both break one-to-one route parity, and host resolution needs a new backend lookup. They can be revisited when multiple storefronts are real.
- **Client-only route guard without `proxy.ts`.** It is simpler, but anonymous users would download the protected shell before redirecting. The proxy hint costs little and adds no new trust.

## Consequences

- Public pages ship meaningful HTML, metadata, and correct `404` status codes.
- The frontend becomes a Node.js server process to build, run, monitor, and scale instead of static files.
- Server-rendered pages must stay deterministic between server and client: no module-scope `window` access, explicit locale and time zone formatting on storefront pages, and portals only after mount.
- Every request that Next.js proxies reaches the backend from the frontend container's IP. The backend honors `X-Forwarded-For` only for configured trusted proxies (`ClientRequestFilter`), and none are configured. Per-IP auth rate-limit buckets are therefore shared by all users, exactly as with Nginx today. This is not a regression; a backend follow-up will add safe forwarded-address handling.
- SSE and WebSocket traffic through Next.js rewrites must be verified in #86. The fallback for WebSockets is a public `NEXT_PUBLIC_BACKEND_WS_URL` with a matching CORS origin.
- Image optimization (`next/image`), a sitemap, and multi-marketplace routing are deferred.

## Rollout and Rollback

The migration ships as five sequential pull requests. `main` stays deployable after each one:

| Order | Issue | Scope |
|---|---|---|
| 1 | #82 | This ADR and the route parity map (documentation only). |
| 2 | #83 | Next.js bootstrap: tooling, providers, store and query-client factories, environment rename, every route ported to an App Router segment as a client page, new 404, router test helpers. Vite runtime removed. The container switches from Nginx to a minimal `next start` image so Docker Compose keeps working. |
| 3 | #84 | Server-rendered public storefront: server data layer, shared query keys, metadata, caching, not-found states, SSR-safe motion. |
| 4 | #85 | Auth boundary: `proxy.ts`, validated `next` deep links, login bounce-back, session status. |
| 5 | #86 | Production hardening: standalone output, non-root image, `/healthz`, SSE/WebSocket proxy verification, Docker Compose and environment updates, CI (lint, format, typecheck, test, build), and Playwright smoke tests including JavaScript-disabled SSR checks. |

To roll back, revert the pull requests in reverse order; each one is self-contained. The last `main` commit before #83 merges is tagged `frontend-vite-final`, so the Vite build, Nginx image, and React Router shell can be restored as a unit by reverting #83 and everything after it.

## Implementation Log

| Part | Pull request | Status |
|---|---|---|
| #82 ADR and route map | #166 | In review |
| #83 Bootstrap | Stacked on #166 | In review |
| #84 Storefront SSR | — | Not started |
| #85 Auth boundary | — | Not started |
| #86 Container and CI | — | Not started |
