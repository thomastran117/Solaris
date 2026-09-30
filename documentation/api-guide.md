# API Guide

ShopWave exposes a JSON REST API from the Spring Boot application. This guide documents shared contracts and stable endpoint families. Controller mappings and DTOs remain the source of truth for individual fields.

## Base URLs

| Environment                         | Base URL                    |
| ----------------------------------- | --------------------------- |
| Complete Docker stack through Nginx | `http://localhost:3000/api` |
| Backend directly                    | `http://localhost:8090/api` |
| Vite development proxy              | `http://localhost:3090/api` |

All backend controller paths are relative to the `/api` servlet context. There is no generated OpenAPI or Swagger endpoint configured in the current build.

## Health Check

`GET /api/health` is public and returns plain text:

```text
OK
```

It is intentionally not wrapped in the JSON response envelope because it uses a string converter.

## Authentication

Local login, signup, verification, OAuth, refresh, logout, and device operations are under `/api/auth`.

- Access tokens are returned to the client and sent as `Authorization: Bearer <token>`.
- Refresh tokens are stored in an HttpOnly cookie named `refreshToken`.
- `POST /auth/refresh` rotates or renews authentication using the cookie.
- `POST /auth/logout` clears the refresh session.
- Protected controller methods use `@RequireAuth`, optionally with allowed roles.
- Company- and vendor-scoped services also verify membership and role within the requested resource.

The login request contract is:

```json
{
  "email": "alice@example.com",
  "password": "Password123!",
  "captcha": "<provider-token>"
}
```

All three values are required. The captcha value must be obtained through the configured frontend/provider flow; do not use a placeholder outside mocked tests.

## Response Envelope

JSON controller responses are wrapped consistently:

```json
{
  "success": true,
  "message": "OK",
  "data": {},
  "error": null,
  "meta": null
}
```

Creation and accepted responses use messages such as `Created` or `Accepted`. Paginated responses put the item collection in `data` and pagination fields in `meta`:

```json
{
  "success": true,
  "message": "OK",
  "data": [],
  "error": null,
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

Cursor pagination uses `nextCursor` and `hasMore` instead of page totals.

## Errors

Handled failures use the same top-level shape:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "details": {
      "email": ["Invalid email format"]
    }
  },
  "meta": null
}
```

Common stable codes include `VALIDATION_ERROR`, `MISSING_PARAM`, `INVALID_PARAM`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, and `INTERNAL_ERROR`. Domain exceptions add their own codes and may provide structured details. Clients should branch on `error.code` rather than parse the human-readable message.

## Major Endpoint Families

| Area                       | Representative path                                 | Notes                                                        |
| -------------------------- | --------------------------------------------------- | ------------------------------------------------------------ |
| Authentication             | `/auth/*`                                           | Login, signup, verification, OAuth, refresh, logout, devices |
| Public marketplace catalog | `/marketplaces/{marketplaceId}/catalog/*`           | Feed, products, compare, product detail, storefronts         |
| Companies                  | `/companies/*`                                      | Public profiles and protected company management             |
| Company products           | `/companies/{companyId}/products/*`                 | Catalog CRUD and product sub-resources                       |
| Orders                     | `/orders/*`                                         | Customer order lifecycle, rates, delivery, stream, tracking  |
| Company fulfillment        | `/companies/{companyId}/orders/*`                   | Merchant actions, risk review, driver assignment             |
| Vendor sub-orders          | `/vendors/{vendorId}/sub-orders/*`                  | Multi-vendor fulfillment and commissions                     |
| Inventory                  | `/companies/{companyId}/inventory/*`                | Locations, stock, adjustments, transfers                     |
| Purchasing                 | `/companies/{companyId}/purchase-orders/*`          | Purchase-order workflow                                      |
| Returns                    | `/orders/{orderId}/returns`                         | Customer return creation and lookup                          |
| Subscriptions              | `/subscriptions/*`                                  | Payment methods and recurring-order lifecycle                |
| Loyalty                    | `/loyalty/*` and `/companies/{companyId}/loyalty/*` | Customer balances and company policies                       |
| Gift cards                 | `/gift-cards/*`                                     | Redemption, balance, listing, and voiding                    |
| B2B quotes                 | `/b2b/quotes/*`                                     | Buyer quote lifecycle                                        |
| Support                    | `/support/tickets/*`                                | Tickets, messages, assignment, status, and priority          |
| Webhooks                   | `/companies/{companyId}/webhooks/*`                 | Registration, verification, delivery history, disable        |

See the [domain guide](domain-guide.md) for the corresponding frontend and backend areas.

## Representative Requests

Public product detail:

```bash
export MARKETPLACE_ID="<marketplace-uuid>"
export PRODUCT_ID="<product-uuid>"
curl "http://localhost:8090/api/marketplaces/$MARKETPLACE_ID/catalog/products/$PRODUCT_ID"
```

Authenticated order list:

```bash
curl -H "Authorization: Bearer $SHOPWAVE_ACCESS_TOKEN" \
  http://localhost:8090/api/orders
```

PowerShell equivalent:

```powershell
$headers = @{ Authorization = "Bearer $env:SHOPWAVE_ACCESS_TOKEN" }
Invoke-RestMethod http://localhost:8090/api/orders -Headers $headers
```

IDs are domain-specific and may be UUIDs or numeric values. Confirm the relevant controller and DTO before scripting a workflow.

## Streaming and WebSockets

`GET /api/orders/{id}/stream` produces server-sent events for order updates. Delivery tracking also includes WebSocket support and authenticated channel interception. Streaming consumers must handle reconnects, expired access tokens, duplicate events, and terminal order states.

## Versioning and Compatibility

The API is not currently exposed under a versioned path. Treat DTO and route changes as compatibility-sensitive:

- Prefer additive response changes.
- Coordinate request or enum changes with the frontend API client and Zod schemas.
- Preserve documented error codes.
- Update this guide and affected integration tests with any shared contract change.
