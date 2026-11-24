# Testing

ShopWave separates fast backend unit tests, database-backed integration tests, frontend component tests, and browser smoke tests. Run the narrowest relevant test while developing, then the complete affected layer before opening a pull request.

## Backend Unit Tests

From `backend/`:

```powershell
.\mvnw.cmd verify "-Dtest=**/*Test" "-Dspring-boot.repackage.skip=true"
```

The CI form adds `--batch-mode`. Maven Wrapper installs the required Maven version, and Java 21 is required.

The `verify` phase produces a JaCoCo report under `backend/target/site/jacoco/` and enforces 80% line coverage across the configured included classes. Integration tests use the `IT` suffix and are excluded by the `**/*Test` selector.

Run one unit test while iterating:

```powershell
.\mvnw.cmd test "-Dtest=MoneyUtilTest"
```

## Backend Integration Tests

Most integration tests extend `AbstractIntegrationIT` and use the `integration-test` profile. Testcontainers provides PostgreSQL and Redis when an external test database is not supplied; provider clients such as mail, OAuth, captcha, Elasticsearch, and S3 are mocked in the common base.

Docker must be running for the Testcontainers path:

```powershell
Set-Location backend
.\mvnw.cmd test "-Dtest=OrderIT" "-Djacoco.skip=true"
```

Run several classes with a comma-separated selector:

```powershell
.\mvnw.cmd test "-Dtest=AuthLoginIT,AuthRefreshIT,AuthTokenIT" "-DreuseForks=false" "-Djacoco.skip=true"
```

The snippets above target PowerShell. On macOS or Linux, use `./mvnw` and retain the quoted `-D` arguments.

CI splits integration tests into eight domain shards and supplies a PostgreSQL service through:

- `SHOPWAVE_TEST_DB_URL`
- `SHOPWAVE_TEST_DB_USERNAME`
- `SHOPWAVE_TEST_DB_PASSWORD`

The authoritative shard membership and timeout flags are in `.github/workflows/ci.yml`. Use that workflow when reproducing a shard exactly.

### Full-Infrastructure Tests

Classes extending `AbstractSearchKafkaIT` instead of `AbstractIntegrationIT` run against real
Elasticsearch and Kafka via Testcontainers, on the `integration-test` plus `fullinfra` profiles.
The default base mocks the Elasticsearch client and search repositories and points Kafka at a dead
broker with `spring.kafka.listener.auto-startup=false`; the full-infrastructure base starts both
containers and lets the indexing workers and `@KafkaListener` consumers run. Read the selected
test's base class before assuming all dependencies are mocked.

Between them these classes cover three kinds of path: write-to-index, search read, and consumer-to-database.

| Class | Shard | Live-infrastructure coverage |
| ----- | ----- | ---------------------------- |
| `ProductIT` | 1 | Product update/delete to Elasticsearch through Kafka; bulk reindex; `product_similarity` rows written by `ProductSimilarityConsumer` |
| `BundleIT` | 2 | Bundle create/delete to Elasticsearch through Kafka |
| `ImportIT` | 3 | `ImportJobConsumer` reads the queued job and upserts `products`; an unreadable CSV lands the job in `FAILED` |
| `MarketplaceCatalogControllerIT` | 3 | Catalog search read path; default relevance ordering with merchandising pins; every `sort` option and `createdAt`/`stock` ordering |
| `SearchSuggestionIT` | 4 | Marketplace and company autocomplete read path |
| `CompanyCatalogSearchIT` | 5 | Plain (non-marketplace) product indexed incrementally through Kafka and found by company catalog search; unqueried product listing reads PostgreSQL |
| `CompanyWebhookIT` | 5 | `OutboundWebhookConsumer` records a `webhook_delivery_logs` row |

Two rules apply when adding to these classes:

- Poll for asynchronous effects with the base class `await(Duration, Callable, Predicate)` helper
  rather than sleeping. Indexing, consumption, and the resulting database write all complete on
  other threads.
- A document is retrievable by id as soon as it is indexed, but is invisible to *search* queries
  until the index refreshes. Call `refreshSearchIndices()` before asserting through an endpoint or
  service that searches, which includes anything reading candidates back out of Elasticsearch.

## Frontend Tests

From `frontend/`:

```powershell
npm ci
npm run test -- --run
```

Vitest uses jsdom and the setup file at `src/test/setup.ts`. Tests normally live beside components or under a view's `__tests__` directory. Vite is installed only as Vitest's transform pipeline; the application itself is built by Next.js.

`src/test/setup.ts` replaces `next/navigation` and `next/link` with controllable test doubles (`src/test/nextNavigation.ts`, `src/test/nextLink.tsx`), so components render without a mounted Next.js app:

```tsx
import { mockRouter, setMockUrl } from "../test/nextNavigation";

setMockUrl("/c/abc/compare?type=product", { params: { id: "abc" } }); // before render
// ...render and interact...
expect(mockRouter.push).toHaveBeenCalledWith("/products/1");
```

The mock URL and router spies reset after every test. The setup file also stubs `matchMedia`, `ResizeObserver`, pointer capture, and `scrollIntoView` for the Radix-based primitives in `src/components/ui`; Radix menus open in tests through `userEvent` clicks or the keyboard.

Design-system tests:

- `src/lib/theme/tokens.test.ts` parses `src/app/globals.css` and checks WCAG AA contrast for every semantic token pair in both themes. Fix a failing token value rather than relaxing the threshold.
- `src/lib/theme/themeScript.test.ts` runs the inline theme script in jsdom (stored choice, OS preference, forced-dark routes, blocked storage).
- `eslint-rules/no-raw-palette.test.mjs` covers the colour lint rule with ESLint's `RuleTester`.

Useful focused commands:

```powershell
npm run test -- SearchBar.test.tsx --run
npm run test -- --watch
```

The second command starts interactive watch mode. CI runs on Node 24. The `Frontend Checks` job runs `npx next typegen`, `npm run typecheck`, `npm run lint`, `npm run test -- --run`, and `npm run build`.

## Static Frontend Checks

```powershell
npm run lint
npm run typecheck
npm run format:check
npm run build
```

`npm run typecheck` runs `tsc --noEmit` over application and test files. `npm run build` runs `next build`, which also type-checks and prerenders static routes, so it catches type and production-bundle failures. Run `npx next typegen` once before `npm run typecheck` in a fresh clone so that `next-env.d.ts` and the route types exist.

`npm run lint` uses `eslint-config-next`. Rules introduced with it, such as the React Compiler hook rules and `@next/next/no-img-element`, are warnings for now; errors fail the command. The project rule `shopwave/no-raw-palette` (`eslint-rules/no-raw-palette.mjs`) and a `no-restricted-imports` ban on `react-icons` and the legacy navy-glass components are errors in folders that have moved to the [design system](../frontend/DESIGN.md) and warnings elsewhere; don't add warnings in files you touch. `npm run format:check` currently reports files that were never formatted on `main`; format files you touch rather than the whole tree. `npm run format` rewrites files and should only be used when you intend to format the working tree.

## Server-Rendered Pages

Vitest cannot render async server components. Cover their logic through the pure helpers instead:

- `src/lib/catalogParams.ts` for URL parsing;
- `src/api/keys.ts` for key shapes, which are a contract between server and client;
- `src/api/server/http.ts` for anonymous requests and error mapping;
- `src/lib/seo.ts` for metadata and JSON-LD escaping;
- `src/hooks/useAnims.ts` for SSR-safe motion.

Then verify the rendered output against a built server:

```powershell
$env:DEFAULT_MARKETPLACE_ID = "<marketplace UUID>"
npm run build
npm run start
curl.exe -s http://localhost:3000/products/<product id>   # product name, canonical link, JSON-LD
curl.exe -s -o NUL -w "%{http_code}" http://localhost:3000/products/00000000-0000-0000-0000-000000000000   # 404
```

Also load a storefront page in a browser with JavaScript disabled. The content must be visible, and with JavaScript enabled the console must show no hydration errors. The browser smoke tests below automate these checks.

## Browser Smoke Tests

The Python Playwright suite in `playwright/tests/` runs against a built frontend:

- **`test_homepage.py`.** Home page, `/healthz`, and a real 404 for unknown routes.
- **`test_storefront_ssr.py`.** Every server-rendered storefront page shows its content with JavaScript disabled and with no `opacity:0` left behind. With JavaScript enabled, pages hydrate without console errors. The file also checks product metadata and JSON-LD escaping, `404` for missing entities, `noindex` for filtered listings, and that server rendering never forwards cookies or `Authorization`.
- **`test_auth_boundary.py`.** It covers:
  - the proxy `307` to `/login?next=…`
  - protected HTML that contains no private data
  - returning to a deep link after logging in
  - the restored-session bounce, which is the Stripe-return case
  - open-redirect rejection
  - the revoked-cookie path
- **`test_theme.py`.** The `dark` class is set before hydration from the system preference, a stored choice overrides it, the toggle persists across reloads, account and seller routes stay forced dark while they migrate (but `/lists/public/*` does not), and pages hydrate without console errors in both themes.
- **`test_cart_checkout.py`.** Add to cart opens the drawer and persists across reloads, `/checkout` requires a session, and placing an order sends an `Idempotency-Key` and the expected body, then shows the payment step ("Payments aren't configured" without a Stripe key).
- **`test_signup.py`.** Signup sends the `captcha` field and shows the inbox step, the login page carries `?next=` to signup, and the `/verify-email` and `/verify-device` links handle valid and spent tokens.

The storefront and auth tests expect the frontend to talk to `playwright/stub_api.py`, a standard-library fixture server for the public storefront, session, signup and verification endpoints (it records auth request bodies so tests can assert their shape). A `refreshToken=valid` cookie is a shopper session; `refreshToken=vendor` is the owner of the fixture company with every capability, for exercising the seller workspace, rather than the real backend. The `Frontend Smoke Tests` CI job runs exactly this setup. To reproduce it locally from the repository root:

```powershell
python playwright/stub_api.py 8090          # in its own terminal
cd frontend
$env:API_INTERNAL_URL = "http://127.0.0.1:8090"
$env:DEFAULT_MARKETPLACE_ID = "11111111-1111-1111-1111-111111111111"
$env:NEXT_PUBLIC_FRONTEND_URL = "http://localhost:3000"
npm run build
Copy-Item -Recurse .next/static .next/standalone/.next/static
Copy-Item -Recurse public .next/standalone/public
node .next/standalone/server.js           # in its own terminal; serves port 3000
```

Then install the dependencies and run the suite:

```powershell
python -m venv .venv
./.venv/Scripts/Activate.ps1
python -m pip install -r playwright/requirements.txt
python -m playwright install chromium
```

```powershell
python -m pytest playwright
```

`BASE_URL` defaults to `http://localhost:3000`. Set it to `http://localhost:3090` for `npm run dev`. Against the full Docker stack, only `test_homepage.py` applies; the storefront and auth tests rely on the stub's fixtures.

## Choosing Tests for a Change

| Change                                                     | Minimum targeted coverage                                    |
| ---------------------------------------------------------- | ------------------------------------------------------------ |
| Pure backend utility or evaluator                          | Matching unit test                                           |
| Controller, security, persistence, or transaction behavior | Unit tests plus affected `*IT` classes                       |
| Migration or entity mapping                                | Integration startup against a clean PostgreSQL database      |
| React component or page logic                              | Vitest and Testing Library                                   |
| API contract used by the frontend                          | Backend integration test plus frontend client/component test |
| Routing or critical user journey                           | Playwright or an explicit manual smoke test                  |
| Docker/configuration                                       | `docker compose config` and service health check             |

## Before a Pull Request

1. Run focused tests during development.
2. Run the full unit/component suite for each affected application.
3. Run affected backend integration classes.
4. Run lint, formatting check, and frontend build for frontend changes.
5. Verify documentation commands if setup, configuration, or operations changed.
6. Record any test that could not run and why.
