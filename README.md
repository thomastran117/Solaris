# ShopWave

ShopWave is a full-stack ecommerce app with:

- `frontend/`: React + TypeScript + Vite
- `backend/`: Spring Boot 3 + Java 21
- PostgreSQL, Redis, and Elasticsearch for local infrastructure

## Docker Quick Start

1. Copy the Docker env template:

```powershell
Copy-Item .env.example .env
```

2. Update any optional values in `.env` if you need OAuth, Stripe, mail, or S3 locally.

3. Build and start the stack:

```powershell
docker compose up --build
```

4. Open the app:

- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8090/api`
- Backend health: `http://localhost:8090/api/health`
- PostgreSQL: `localhost:5433`
- Redis: `localhost:6379`
- Elasticsearch: `http://localhost:9200`

## Notes

- The frontend is served by Nginx in Docker and proxies `/api` to the Spring Boot container.
- Vite dev mode also proxies `/api` to `http://localhost:8090`, so frontend code can use the same `/api` base path in both local dev and Docker.
- `docker-compose.yml` uses named volumes for PostgreSQL, Redis, and Elasticsearch data.
- The schema is owned by **Flyway**, not Hibernate. `SPRING_JPA_HIBERNATE_DDL_AUTO` is
  `validate`, so the app fails to start if the entity mappings and the migrated schema
  disagree — that means a migration is missing, not that you should change `ddl-auto`.
- Because the schema is no longer recreated on every boot, data now survives a restart.
  To start clean, run `docker compose down -v`.

### Database migrations

Migrations live in `backend/src/main/resources/db/migration` and run automatically at
startup. To change the schema, add a new `V<n>__<description>.sql` — never edit an
applied migration, including `V1__baseline.sql`, since Flyway verifies its checksum.

### Upgrading from the MySQL version

The MySQL container and its `mysql-data` volume are gone. Remove the stale volume with
`docker compose down -v` (or `docker volume rm shopwave_mysql-data`) before starting.

## Useful Commands

Start in the background:

```powershell
docker compose up --build -d
```

Stop everything:

```powershell
docker compose down
```

Stop and remove volumes too:

```powershell
docker compose down -v
```
