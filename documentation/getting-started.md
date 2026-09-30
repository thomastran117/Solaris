# Getting Started

## Prerequisites

The quickest path requires Git and Docker with Compose. A hybrid local workflow additionally requires:

- Java 21; the repository includes Maven Wrapper scripts
- Node.js compatible with the checked-in npm lockfile
- Python 3 and Chromium only when running the Playwright suite

Allocate enough Docker memory for Elasticsearch and Kafka in addition to the database and application containers.

## Run the Complete Stack

From the repository root:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

On macOS or Linux, copy the template with `cp .env.example .env`.

Compose starts six services: `postgres`, `redis`, `elasticsearch`, `kafka`, `backend`, and `frontend`. The backend waits for the four infrastructure health checks before starting. First startup takes longer while images and dependencies are downloaded and Flyway creates the schema.

Verify the result:

```powershell
Invoke-RestMethod http://localhost:8090/api/health
```

Then open <http://localhost:3000>. The health endpoint returns the plain text value `OK`.

Useful lifecycle commands:

```powershell
docker compose up --build -d
docker compose ps
docker compose logs -f backend
docker compose down
```

Use `docker compose down -v` only when you intentionally want to delete the local PostgreSQL, Redis, Elasticsearch, and Kafka data volumes.

## Hybrid Local Development

Running dependencies in Docker while starting the applications locally gives faster edit/reload cycles.

1. Start infrastructure:

   ```powershell
   docker compose up -d postgres redis elasticsearch kafka
   ```

2. Start the backend from `backend/`:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

3. Start the frontend from `frontend/` in another terminal:

   ```powershell
   npm ci
   npm run dev
   ```

4. Open <http://localhost:3090>. Vite proxies `/api` to `http://localhost:8090`.

On macOS or Linux, use `./mvnw` instead of `.\mvnw.cmd`.

The checked-in `.env.example` is designed for the complete Compose stack. For a locally started backend, the infrastructure ports exposed to the host must match its environment. In particular, the template maps PostgreSQL to `5433`, while the backend's code default is `5432`. Set `DB_URL=jdbc:postgresql://localhost:5433/shopwave` when using the template's PostgreSQL port. Kafka's host port is `9093`, so set `KAFKA_BOOTSTRAP_SERVERS=localhost:9093` for a local backend.

## Development Data

The default backend profile is `dev`. On startup, `DevDataSeeder` creates users, companies, products, reviews, inventory, orders, loyalty records, subscriptions, tickets, and other demonstration data. Seeding is idempotent where the individual seeders locate existing records before creating them.

All seeded users use `Password123!` and are strictly for local development:

| Role          | Email                            |
| ------------- | -------------------------------- |
| Administrator | `admin@shopwave.dev`             |
| Merchant      | `merchant.tech@shopwave.dev`     |
| Merchant      | `merchant.style@shopwave.dev`    |
| Merchant      | `merchant.wellness@shopwave.dev` |
| Merchant      | `merchant.home@shopwave.dev`     |
| Merchant      | `merchant.sport@shopwave.dev`    |
| Customer      | `alice@example.com`              |
| Customer      | `bob@example.com`                |
| Customer      | `carol@example.com`              |

Local login requests still require a non-empty captcha value at the request-validation layer. The UI and backend integration settings determine whether an external verification call is active.

## Common Startup Problems

### Backend waits or restarts

Run `docker compose ps` and inspect the unhealthy dependency with `docker compose logs <service>`. Elasticsearch generally takes the longest to become ready.

### Flyway or Hibernate validation fails

Flyway owns the schema and Hibernate uses `ddl-auto=validate`. Do not switch Hibernate to `update`. For disposable local state, reset volumes; for a real schema change, add a new migration as described in [operations](operations.md#database-migrations).

### A port is already in use

Change the corresponding host-side value in `.env`, then recreate the affected container. See [configuration](configuration.md#compose-ports).

### OAuth, mail, shipping, or payment features fail

Most external integrations are intentionally blank in the local template. The core stack can start without them because Compose disables strict OAuth configuration validation. Configure only the service you are exercising and consult [configuration](configuration.md#optional-integrations).

### Login cookies are not retained over HTTP

The application default makes refresh cookies secure. For plain-HTTP local backend development, set `COOKIE_SECURE=false`; never use that value for an HTTPS deployment.
