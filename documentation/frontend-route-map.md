# Frontend Route Parity Map

This page maps every route the frontend serves today to its App Router location after the Next.js migration ([ADR 0001](decisions/0001-nextjs-app-router.md), epic #66). It is the parity checklist for #83–#86: a migration pull request is complete only when every row it touches resolves at the same URL, survives a hard refresh, and behaves as described in the **Target** columns.

**Current** columns describe `frontend/src/App.tsx` as of `main` on 2026-11-22. **Target** columns are the accepted design and are not implemented until the pull request named in the **PR** column merges.

## Conventions

- **View.** The existing page component. The migration renames `frontend/src/pages` to `frontend/src/views` without changing file names (Next.js would otherwise treat `src/pages` as a Pages Router directory). Paths below are relative to that directory.
- **Segment.** The target file under `frontend/src/app`. Route groups in parentheses do not appear in URLs.
- **Render modes.**
  - `SSR` renders on the server per request. `ISR 60` renders on the server and is cached for 60 seconds. Both use public, anonymous data only.
  - `Static` is prerendered at build time.
  - `Shell` server-renders the layout chrome. The view's data loads in the browser.
  - `Guarded` is a `Shell` route inside the account or merchant auth boundary.
- **Access.**
  - `Public` needs no session.
  - `Session` needs a signed-in user. Today that means an access token is present in Redux.
  - Merchant and staff routes also need a session. The table lists the company capability or platform role that makes each Navbar link visible. The backend enforces the real authorization in every case.
- **Prefetch keys.** These are the TanStack Query keys a server-rendered route seeds through `HydrationBoundary`. They must match the client query keys exactly. `mp` is the configured default marketplace ID.

## Public Storefront — Server Rendered (#84)

| # | URL | View | Segment | Mode | Prefetch keys | Metadata and robots | Not found |
|---|---|---|---|---|---|---|---|
| 1 | `/` | `HomePage.tsx` | `(storefront)/page.tsx` | ISR 60 | `["home","featured-collections",mp]` | Static title and description; canonical `/` | — |
| 2 | `/browse` | `BrowsePage.tsx` | `(storefront)/browse/page.tsx` | SSR (query-driven) | `["catalog","browse",mp,q,category,brand,minPrice,maxPrice,sort,direction,page]` from the shared parameter parser | Canonical keeps `category`, `brand`, `page`; `noindex` when `q`, `minPrice`, or `maxPrice` is set | — |
| 3 | `/products/:id` | `ProductPage.tsx` | `(storefront)/products/[id]/page.tsx` | ISR 60 | `["product",mp,id]` | Product name and description, Open Graph image, canonical, `Product` JSON-LD | Unknown, unlisted, or malformed ID → `404` |
| 4 | `/c/:id` | `CompanyPage.tsx` | `(storefront)/c/[id]/page.tsx` | ISR 60 | `["company","public",id]`, `["company","catalog",id,…]` (page 0), `["announcements",id,0]` | Company name and description; canonical | Unknown company → `404` |
| 5 | `/collections/:slug` | `CollectionPage.tsx` | `(storefront)/collections/[slug]/page.tsx` | ISR 60 | `["collection","detail",mp,slug]`, `["collection","members",mp,slug]`, `["collection","members-catalog",mp,slug,productIds]` | Collection title and description; canonical | Unknown slug → `404` |
| 6 | `/kits/:companyId/:kitId` | `KitPage.tsx` | `(storefront)/kits/[companyId]/[kitId]/page.tsx` | ISR 60 | `["kit",companyId,kitId]` | Kit name; canonical | Unknown or unlisted kit → `404` |
| 7 | `/lists/public/:slug` | `PublicSavedListPage.tsx` | `(storefront)/lists/public/[slug]/page.tsx` | ISR 60 | `["savedLists","public",slug]` (`KEYS.public` in `hooks/useSavedLists.ts`) | List title; `noindex` (user-generated) | Unknown or private slug → `404` |

Per-user data on these pages is never prefetched; it loads in the browser after hydration. That covers follow status (`["follow","status",id]`), back-in-stock subscriptions, price watches, and the loyalty policy and account.

## Public — Static or Client Interactive

| # | URL | View | Segment | Mode | PR | Notes |
|---|---|---|---|---|---|---|
| 8 | `/terms-and-conditions` | `TermsAndConditionPage.tsx` | `(storefront)/terms-and-conditions/page.tsx` | Static | #83 | The page passes today's props: `companyName`, `contactEmail`, `lastUpdated`. |
| 9 | `/privacy` | `PrivacyPage.tsx` | `(storefront)/privacy/page.tsx` | Static | #83 | The page passes `companyName`, `contactEmail`, `dpoEmail`, `lastUpdated`. |
| 10 | `/c/:id/compare` | `ComparisonPage.tsx` | `(storefront)/c/[id]/compare/page.tsx` | Shell | #83 | Driven by query params `type` and `ids`; wrapped in `Suspense` for `useSearchParams`. `noindex`. |
| 11 | `/gift-cards` | `GiftCardsPage.tsx` | `(storefront)/gift-cards/page.tsx` | Shell | #83 | Balance lookup works anonymously. The purchased list (`["gift-cards","purchased",page]`) needs a session and stays client-only. |
| 12 | `/hello` | `HelloTestPage.tsx` | `(storefront)/hello/page.tsx` | Shell | #83 | Development test page. Ported with `noindex` for parity; proposed for deletion in a follow-up. |

## Authentication Flow

| # | URL | View | Segment | Mode | PR | Notes |
|---|---|---|---|---|---|---|
| 13 | `/login` | `LoginPage.tsx` | `(auth)/login/page.tsx` | Shell | #83, #85 | #85 reads a validated `next` parameter and returns there after login. It also bounces automatically when a session is restored on arrival. Today the page always navigates to `/dashboard`. |
| 14 | `/auth/google` | `AuthCallback.tsx` | `(auth)/auth/google/page.tsx` | Shell | #83 | Google OAuth popup redirect target. It reads `id_token` from the URL fragment, which never reaches the server, so the page stays client-only. The path must not change because it is the registered redirect URI. Fix: the fallback currently sends the user to the unrouted `/auth`; it becomes `/login`. |
| 15 | `/invite/accept/:token` | `InviteAcceptPage.tsx` | `(auth)/invite/accept/[token]/page.tsx` | Shell | #83 | Already redirects anonymous users to `/login?next=<path>`. The round-trip starts working once #85 makes the login page honor `next`. `noindex`. |

## Customer Account — Guarded (#83 port, #85 boundary)

Today all of these sit inside the pathless `<ProtectedRoute/>`. That component redirects to `/login` when Redux has no access token, and it drops the original destination.

Target: `(account)/layout.tsx` wraps them in the client `RequireAuth` guard and sets `noindex`. `proxy.ts` (#85) redirects to `/login?next=…` when the `refreshToken` cookie is absent.

| # | URL | View | Segment | Access |
|---|---|---|---|---|
| 16 | `/feed` | `FeedPage.tsx` | `(account)/feed/page.tsx` | Session |
| 17 | `/following` | `FollowingPage.tsx` | `(account)/following/page.tsx` | Session |
| 18 | `/dashboard` | `DashboardPage.tsx` | `(account)/dashboard/page.tsx` | Session |
| 19 | `/lists` | `SavedListsPage.tsx` | `(account)/lists/page.tsx` | Session |
| 20 | `/lists/:id` | `SavedListDetailPage.tsx` | `(account)/lists/[id]/page.tsx` | Session |
| 21 | `/account` | `AccountPage.tsx` | `(account)/account/page.tsx` | Session |
| 22 | `/loyalty` | `LoyaltyPage.tsx` | `(account)/loyalty/page.tsx` | Session |
| 23 | `/price-watches` | `PriceWatchesPage.tsx` | `(account)/price-watches/page.tsx` | Session |
| 24 | `/feedback` | `FeedbackPage.tsx` | `(account)/feedback/page.tsx` | Session |
| 25 | `/orders` | `OrdersPage.tsx` | `(account)/orders/page.tsx` | Session |
| 26 | `/orders/:id` | `OrderDetailPage.tsx` | `(account)/orders/[id]/page.tsx` | Session |
| 27 | `/b2b/quotes` | `B2BQuotesPage.tsx` | `(account)/b2b/quotes/page.tsx` | Session |

Notes:

- **`/lists/:id` vs `/lists/public/:slug`.** The two live in different route groups. Their URLs cannot collide because the static `public` segment outranks `[id]`. The protected-path matcher must exclude the `/lists/public/` prefix.
- **`/account` returns from Stripe.** It reads Stripe Checkout and Billing Portal return parameters from the query string. That arrival is a cross-site navigation, so the `SameSite=Strict` refresh cookie is not sent. The proxy sends the user through `/login?next=/account?…`, the same-site session restore succeeds there, and the user returns automatically ([ADR 0001](decisions/0001-nextjs-app-router.md#auth-boundary)).
- **`/orders/:id` keeps its live updates.** It subscribes to the SSE stream `/api/orders/{id}/stream`. #86 verifies SSE through the Next.js rewrite.

## Merchant and Staff — Guarded (#83 port, #85 boundary)

Target: `(merchant)/layout.tsx` uses the same `RequireAuth` guard and sets `noindex`. All 26 URLs keep their exact paths under `(merchant)/admin/…`. Capability and role checks stay in the UI. The backend enforces membership, role, and tenant scope.

| # | URL | View (`admin/…`) | Segment (`(merchant)/admin/…`) | Navbar exposure today |
|---|---|---|---|---|
| 28 | `/admin/b2b-quotes` | `AdminB2BQuotesPage.tsx` | `b2b-quotes/page.tsx` | Company member with `MANAGE_QUOTES` |
| 29 | `/admin/products` | `AdminProductsPage.tsx` | `products/page.tsx` | Company member with `MANAGE_PRODUCTS` or `FULFILL_ORDERS` ("Admin") |
| 30 | `/admin/bundles` | `AdminBundlesPage.tsx` | `bundles/page.tsx` | Company member with `MANAGE_PRODUCTS` |
| 31 | `/admin/kits` | `AdminKitsPage.tsx` | `kits/page.tsx` | Not in Navbar; linked from merchant pages |
| 32 | `/admin/kits/new` | `AdminKitEditor.tsx` | `kits/new/page.tsx` | Not in Navbar |
| 33 | `/admin/kits/:id/edit` | `AdminKitEditor.tsx` | `kits/[id]/edit/page.tsx` | Not in Navbar |
| 34 | `/admin/products/new` | `AdminProductEditor.tsx` | `products/new/page.tsx` | Not in Navbar; the page checks capabilities |
| 35 | `/admin/products/:id` | `AdminProductEditor.tsx` | `products/[id]/page.tsx` | Not in Navbar; the page checks capabilities |
| 36 | `/admin/collections` | `AdminCollectionsPage.tsx` | `collections/page.tsx` | Not in Navbar |
| 37 | `/admin/collections/new` | `AdminCollectionEditor.tsx` | `collections/new/page.tsx` | Not in Navbar |
| 38 | `/admin/collections/:id/edit` | `AdminCollectionEditor.tsx` | `collections/[id]/edit/page.tsx` | Not in Navbar |
| 39 | `/admin/collections/:id/products` | `AdminCollectionProductsPage.tsx` | `collections/[id]/products/page.tsx` | Not in Navbar |
| 40 | `/admin/team` | `AdminTeamPage.tsx` | `team/page.tsx` | Company member with `MANAGE_COMPANY`; the page checks capabilities |
| 41 | `/admin/bulk-import` | `AdminBulkImportPage.tsx` | `bulk-import/page.tsx` | Not in Navbar |
| 42 | `/admin/reports` | `AdminReportsPage.tsx` | `reports/page.tsx` | Not in Navbar |
| 43 | `/admin/loyalty` | `AdminLoyaltyPage.tsx` | `loyalty/page.tsx` | Company member with `MANAGE_PROMOTIONS`; the page checks role |
| 44 | `/admin/feedback` | `AdminFeedbackPage.tsx` | `feedback/page.tsx` | Platform role `ADMIN` |
| 45 | `/admin/announcements` | `AdminAnnouncementsPage.tsx` | `announcements/page.tsx` | Not in Navbar |
| 46 | `/admin/webhooks` | `AdminWebhooksPage.tsx` | `webhooks/page.tsx` | Not in Navbar |
| 47 | `/admin/marketing` | `AdminMarketingPage.tsx` | `marketing/page.tsx` | Company member with `MANAGE_PROMOTIONS` |
| 48 | `/admin/purchase-orders` | `AdminPurchaseOrdersPage.tsx` | `purchase-orders/page.tsx` | Company member with `MANAGE_PRODUCTS`; query-param driven, so it needs `Suspense` |
| 49 | `/admin/inventory-transfers` | `AdminInventoryTransfersPage.tsx` | `inventory-transfers/page.tsx` | Company member with `MANAGE_PRODUCTS` |
| 50 | `/admin/orders` | `CompanyOrdersPage.tsx` | `orders/page.tsx` | Company member with `FULFILL_ORDERS` ("Fulfillment") |
| 51 | `/admin/orders/:orderId` | `CompanyOrderDetailPage.tsx` | `orders/[orderId]/page.tsx` | Not in Navbar; linked from `/admin/orders` |
| 52 | `/admin/drivers` | `DriverDispatchPage.tsx` | `drivers/page.tsx` | Company member with `FULFILL_ORDERS`; uses STOMP WebSocket `/api/ws/delivery` (verified in #86) |
| 53 | `/admin/disputes` | `AdminDisputesPage.tsx` | `disputes/page.tsx` | Platform role `SUPPORT`, `MODERATOR`, or `ADMIN` |

The Navbar column records which signed-in users see each link today; capability checks come from `useCompanyCapabilities` in `hooks/useCompanyRole.ts`. It is informational only. Direct navigation to any of these URLs with a valid session reaches the page, and the backend rejects calls the user is not authorized to make.

## New Routes and Files

| URL or file | Segment | PR | Purpose |
|---|---|---|---|
| Any unmatched URL | `app/not-found.tsx` | #83 | Navy-styled 404 page with a real `404` status. Today unknown URLs render an empty body with status `200`. |
| Storefront entity misses | `(storefront)/not-found.tsx` | #84 | Product, company, collection, kit, or list not found. |
| `/robots.txt` | `app/robots.ts` | #84 | Disallows the account and merchant prefixes. |
| `/healthz` | `app/healthz/route.ts` | #86 | Container health check. It sits outside `/api/*`, which is rewritten to the backend. |
| `/api/*` | `next.config.ts` rewrite | #83 | Forwards same-origin API calls to `API_INTERNAL_URL`. It replaces the Vite dev proxy and Nginx `location /api/`. |

## Deliberate Retirements and Fixes

These links and files exist today but have no working route. The migration removes or fixes them instead of porting them.

| Item | Location | Decision | PR |
|---|---|---|---|
| `/about` link | `components/Navbar.tsx` (header link and "More" menu) | Remove. `AboutPage.tsx` is an empty file and is not routed. | #83 |
| `/contact` link | `components/Navbar.tsx` ("More" menu) | Remove. `ContactPage.tsx` is an empty file and is not routed. | #83 |
| `/compare` link | `components/Navbar.tsx` | Remove. Comparison is reached through `/c/:id/compare` from a merchant page. | #83 |
| `/profiles` link | `components/Navbar.tsx` ("More" menu) | Remove; no such page exists. | #83 |
| `/profile` link | `components/Navbar.tsx` (account menu) | Point at `/account`, which owns the profile screen. | #83 |
| `/auth` fallback | `pages/AuthCallback.tsx` | Point at `/login`. | #83 |
| `Sidebar` | `components/Sidebar.tsx` | Delete. It is imported by `App.tsx` but never rendered. | #83 |
| Empty pages | `pages/AboutPage.tsx`, `pages/ContactPage.tsx` | Delete. | #83 |

## Refresh and Deep-Link Behavior

- **Hard refresh.** Every route above resolves on a hard refresh or a direct visit. Today Nginx `try_files … /index.html` and the Vite dev server provide this. After the migration, App Router segments do, and no SPA fallback is needed.
- **Protected deep links.** Today an anonymous visitor lands on `/login` and loses the destination. After #85 they land on `/login?next=<original path and query>` and return there after signing in. They also return when a session already exists in the cookie.
- **Query-string state.** `/browse`, `/c/:id`, `/c/:id/compare`, and `/admin/purchase-orders` keep their filters in the URL. The migration preserves the parameter names and keeps filter changes from scrolling the page.
