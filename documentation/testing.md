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

The mock URL and router spies reset after every test.

Useful focused commands:

```powershell
npm run test -- SearchBar.test.tsx --run
npm run test -- --watch
```

The second command starts interactive watch mode. CI uses Node 20 and `npm run test -- --run`.

## Static Frontend Checks

```powershell
npm run lint
npm run typecheck
npm run format:check
npm run build
```

`npm run typecheck` runs `tsc --noEmit` over application and test files. `npm run build` runs `next build`, which also type-checks and prerenders static routes, so it catches type and production-bundle failures. Run `npx next typegen` once before `npm run typecheck` in a fresh clone so that `next-env.d.ts` and the route types exist.

`npm run lint` uses `eslint-config-next`. Rules introduced with it, such as the React Compiler hook rules and `@next/next/no-img-element`, are warnings for now; errors fail the command. `npm run format:check` currently reports files that were never formatted on `main`; format files you touch rather than the whole tree. `npm run format` rewrites files and should only be used when you intend to format the working tree.

## Browser Smoke Test

The Python Playwright suite currently contains a homepage smoke test. Install its dependencies from the repository root:

```powershell
python -m venv .venv
./.venv/Scripts/Activate.ps1
python -m pip install -r playwright/requirements.txt
python -m playwright install chromium
```

Start the application, then override the historical test default with the URL you are actually serving:

```powershell
$env:BASE_URL = "http://localhost:3000"
python -m pytest playwright
```

For `npm run dev`, use `http://localhost:3090` instead. The checked-in fixture defaults to `http://localhost:3040`, so setting `BASE_URL` is required for the documented Docker and development-server workflows.

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
