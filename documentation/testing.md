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

Some full-infrastructure search/Kafka tests use a separate base and require additional containers. Read the selected test's base class before assuming all dependencies are mocked.

## Frontend Tests

From `frontend/`:

```powershell
npm ci
npm run test -- --run
```

Vitest uses jsdom and the setup file at `src/test/setup.ts`. Tests normally live beside components or under a page's `__tests__` directory.

Useful focused commands:

```powershell
npm run test -- SearchBar.test.tsx --run
npm run test -- --watch
```

The second command starts interactive watch mode. CI uses Node 20 and `npm run test -- --run`.

## Static Frontend Checks

```powershell
npm run lint
npm run format:check
npm run build
```

`npm run build` runs the TypeScript project build before Vite, so it catches type and production-bundle failures. `npm run format` rewrites files and should only be used when you intend to format the working tree.

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

For Vite development, use `http://localhost:3090` instead. The checked-in fixture defaults to `http://localhost:3040`, so setting `BASE_URL` is required for the documented Docker and Vite workflows.

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
