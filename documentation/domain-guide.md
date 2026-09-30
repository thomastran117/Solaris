# Domain Guide

Use this guide to find the main application surface for a change. Backend HTTP controllers live under `backend/src/main/java/backend/controllers/impl`; frontend pages, components, API clients, schemas, and types are grouped under their corresponding directories in `frontend/src`.

This is a capability map, not an endpoint inventory. The [API guide](api-guide.md) explains shared HTTP behavior.

## Identity and Customer Accounts

The authentication domain supports signup, email verification, local login, OAuth, access-token refresh, logout, known-device management, rate limiting, and device verification. Profile capabilities include addresses, payment methods, preferences, credits, and account data.

- Backend: `controllers/impl/auth`, `controllers/impl/profile`, and security/configuration packages
- Frontend: `LoginPage`, `AccountPage`, `AuthInitializer`, `ProtectedRoute`, and `stores/authSlice.ts`

## Marketplace and Catalog

Marketplace catalog APIs provide feeds, filtering, product detail, comparison, vendor storefronts, collections, kits, availability, recommendations, search suggestions, and view activity. Company catalog APIs manage products, images, options, variants, attributes, relationships, bundles, merchandising, and product history.

- Backend: `controllers/impl/marketplace`, `products`, `collections`, `search`, and `pricing`
- Frontend: `BrowsePage`, `ProductPage`, `ComparisonPage`, `CompanyPage`, `CollectionPage`, and `KitPage`

PostgreSQL is authoritative. Redis accelerates reads and Elasticsearch supplies search projections.

## Saved Lists, Reviews, and Product Q&A

Customers can create private or public saved lists, add and reorder items, review purchases, upload review media, vote on helpfulness, report content, ask product questions, and answer them. Moderation and merchant response paths are implemented on the backend.

- Backend: saved-list, review, Q&A, and report controllers and services
- Frontend: `components/savedlist`, `components/reviews`, `components/qa`, and the list pages

## Companies, Vendors, and Teams

Companies expose public profiles and merchant-managed settings. Vendor workflows cover marketplace onboarding, tax details, documents, approval states, payouts, commissions, and vendor analytics. Team management adds invitations and role-based company access.

- Backend: `controllers/impl/company` and `controllers/impl/vendors`
- Frontend: company pages and the `pages/admin` management workspace

Company-scoped APIs normally include `/companies/{companyId}`; marketplace vendor APIs include `/marketplaces/{marketplaceId}/vendors`.

## Orders, Checkout, and Fulfillment

The order domain handles pricing, stock reservation, payment intents, tax, shipping-rate selection, delivery slots, cancellation, reorder, status history, tracking, SSE updates, driver location, partial refunds, replacements, issues, and disputes. Multi-vendor orders are split into sub-orders for vendor fulfillment.

- Backend: `controllers/impl/orders`, `payments`, returns, shipping, tax, and risk services
- Frontend: order pages and `components/order`

Order and inventory changes are consistency-critical. Preserve transaction boundaries, locking, idempotency, and compensation when changing these flows.

## Inventory and Purchasing

Company operations include stock by location, adjustments, low-stock/restock workflows, supplier purchase orders, and inventory transfers between locations.

- Backend: `controllers/impl/inventory`
- Frontend: admin products, purchase orders, bulk imports, and inventory transfers

Flyway migrations are required for schema changes. Redis must not become the inventory source of truth.

## Subscriptions, Returns, and Gift Cards

Subscription APIs manage saved payment methods, recurring orders, pause/resume, skip-next, update, and cancellation. Return APIs support customer requests, merchant approval, rejection, inspection, and company return locations. Gift-card APIs support purchase-related management, balance checks, redemption, listing, and administrative voiding.

- Backend: `controllers/impl/subscriptions`, `returns`, and `giftcards`
- Frontend: `GiftCardsPage` and order/account surfaces; some backend capabilities do not yet have complete customer-facing workflows

## Loyalty, Promotions, and Marketing

The promotion engine supports coupon and rule evaluation, product bundles, tiered pricing, loyalty accounts and ledgers, tiers, referrals, bonuses, and administrative adjustments. Marketing includes workflows, enrollments, announcements, notification preferences, and delivery channels.

- Backend: `controllers/impl/promotions`, `marketing`, and `notification`
- Frontend: loyalty pages and marketing/announcement administration

Asynchronous messages are delivered through configured Kafka topics; external email, push, and SMS providers are optional in local development.

## B2B Commerce

B2B buyers can request and manage quotes, while merchants review, price, accept, reject, and convert quote activity according to the implemented workflow. Company order and invoicing services support related wholesale operations.

- Backend: `controllers/impl/b2b` and company-order services
- Frontend: `B2BQuotesPage` and `AdminB2BQuotesPage`

## Support, Feedback, and Moderation

Support tickets contain customer and staff messages, status, assignment, priority, and internal notes. Separate flows cover order issues, feedback, product/review reports, user administration, and moderation.

- Backend: `controllers/impl/support`, `feedback`, `reports`, and `admin`
- Frontend: feedback/report components and administrative report, feedback, dispute, and order pages

## Analytics and Operations

Company and marketplace analytics expose revenue, products, orders, refunds, payouts, forecasting, pricing, and operational summaries. Import jobs, outbound webhooks, and audit/history records support merchant automation.

- Backend: `controllers/impl/analytics`, `imports`, and `webhook`
- Frontend: dashboard, bulk-import, webhook, and reporting pages

Outbound webhook destinations are subject to SSRF checks, encrypted secrets, verification, delivery history, and disable controls.

## Capability Status

The presence of a backend controller does not guarantee a complete end-user journey in the frontend. When documenting or extending a feature, verify all three layers:

1. HTTP and service behavior in the backend
2. API client, schema, and type support in the frontend
3. A reachable route and usable page flow in `frontend/src/App.tsx`

Future possibilities belong in the [candidate roadmap](roadmap.md), not in this current-state map.
