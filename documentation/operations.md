# Operations and Troubleshooting

This guide covers local and development operations. The repository does not define a production platform, backup policy, SLA, or release process.

## Health and Service State

Check the public backend probe:

```powershell
Invoke-WebRequest http://localhost:8090/api/health
```

A healthy application responds with HTTP 200 and `OK`. This proves the web process is responding; use Compose health and logs to investigate its dependencies:

```powershell
docker compose ps
docker compose logs --tail 200 backend
docker compose logs --tail 200 postgres
docker compose logs --tail 200 redis
docker compose logs --tail 200 elasticsearch
docker compose logs --tail 200 kafka
```

Follow a service during reproduction with `docker compose logs -f <service>`.

## Database Migrations

Flyway is the only schema owner. Migrations live in:

```text
backend/src/main/resources/db/migration
```

To change the schema:

1. Identify the highest committed version.
2. Add `V<next>__<description>.sql` using a stable, descriptive name.
3. Update JPA mappings and repositories in the same change.
4. Start against a clean PostgreSQL database and run the affected integration tests.
5. Verify Hibernate completes `ddl-auto=validate`.

Never edit an applied migration, including `V1__baseline.sql`. Flyway records checksums, and changing history breaks existing databases. Never switch Hibernate to `update` to conceal a missing migration.

The application enables Flyway validation, disables automatic baselining, and disables Flyway clean. These safeguards are intentional.

## Resetting Local State

Restart containers while preserving state:

```powershell
docker compose restart
```

Rebuild application images:

```powershell
docker compose up --build -d backend frontend
```

Delete all Compose-managed local data and recreate the stack:

```powershell
docker compose down -v
docker compose up --build
```

The `-v` command permanently removes this Compose project's PostgreSQL, Redis, Elasticsearch, and Kafka volumes. Use it only for disposable local data.

## Search Troubleshooting

PostgreSQL is authoritative; Elasticsearch is a derived read model.

- Confirm Elasticsearch is healthy before debugging application indexing.
- Inspect backend logs for queue saturation, retry, or dead-letter messages.
- Check `ES_HOST`, `ES_PORT`, `ES_SCHEME`, and credentials.
- Use the product reindex administration path only with an authorized account and after understanding the workload.
- A search miss immediately after a write can be eventual-consistency delay rather than lost database data.

## Kafka Troubleshooting

Check broker health and backend connectivity:

```powershell
docker compose ps kafka
docker compose logs --tail 200 kafka
docker compose logs --tail 200 backend
```

Within Compose, the backend uses `kafka:9092`. A backend running on the host must use the published host port, `localhost:9093` with the template.

Topic names are application configuration. Renaming one requires coordinated producer, consumer, deployment, and monitoring changes. Inspect the relevant `*.dlq` topic when a flow exhausts its retries.

## Redis Troubleshooting

Redis holds caches and transient coordination state. Verify the backend uses `redis:6379` inside Compose or the published host port outside it.

- Cache loss should not delete PostgreSQL business data.
- Clearing Redis may invalidate sessions, rate-limit state, reservations, or locks, so do not treat it as a harmless cache flush on a shared environment.
- Investigate lock and reservation timing before changing `LOCK_TTL_SECONDS`, `ORDER_RESERVATION_TTL_SECONDS`, or `ORDER_STALE_MINUTES`.

## External Integrations

Provider-backed features require their own credentials:

- Stripe: payments, webhooks, saved payment methods, and premium billing
- EasyPost and AfterShip: rates and tracking
- SMTP, Firebase, and Twilio: email, push, and SMS
- S3-compatible storage: upload and public media URLs
- Google, Microsoft, and Apple: OAuth token verification

The core local stack leaves these credentials blank. Depending on the path, the application may return a provider-not-configured error, use a documented fallback, retry a transient call, open a circuit breaker, or enqueue failed asynchronous work. Check the owning service and tests before relying on a fallback.

Webhook troubleshooting must preserve raw request bodies where signature verification requires them. Never log bearer tokens, OAuth tokens, payment secrets, refresh cookies, or full provider payloads containing sensitive data.

## Common Failure Patterns

### Backend fails immediately with a JWT error

Ensure `JWT_SECRET` is present and contains sufficient HS512 key material. The template value is only for local Compose startup.

### Backend cannot connect during hybrid development

Container service names such as `postgres` and `kafka` resolve only inside Compose. A host process needs `localhost` and the published ports described in [configuration](configuration.md#compose-ports).

### Frontend receives 404 for every API call

Keep `VITE_BACKEND_URL=/api` when using the Vite or Nginx proxy. If calling the backend directly, include the `/api` servlet context.

### Browser rejects a request

Check `CORS_ALLOWED_ORIGINS` for the exact scheme, host, and port. For refresh-cookie problems, also check `COOKIE_SECURE` and `COOKIE_SAME_SITE`.

### Database mapping validation fails

Compare the entity change with the Flyway schema. Add a migration or correct the mapping; do not relax validation.

## Operational Change Checklist

- Document new required environment variables and safe defaults.
- Add health, timeout, retry, and failure behavior appropriate to the dependency.
- Avoid sensitive values in exceptions and logs.
- Make consumers and webhook handlers idempotent.
- Ensure asynchronous failure has a bounded retry or dead-letter path.
- Test startup from clean state and upgrade from the existing schema.
- Update [architecture](architecture.md), [configuration](configuration.md), and this runbook when operational behavior changes.
