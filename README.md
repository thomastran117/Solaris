# ShopWave

ShopWave is a full-stack ecommerce app with:

- `frontend/`: React + TypeScript + Vite
- `backend/`: Spring Boot 3 + Java 21
- MySQL, Redis, and Elasticsearch for local infrastructure

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
- MySQL: `localhost:3306`
- Redis: `localhost:6379`
- Elasticsearch: `http://localhost:9200`

## Notes

- The frontend is served by Nginx in Docker and proxies `/api` to the Spring Boot container.
- Vite dev mode also proxies `/api` to `http://localhost:8090`, so frontend code can use the same `/api` base path in both local dev and Docker.
- `docker-compose.yml` uses named volumes for MySQL, Redis, and Elasticsearch data.
- The backend is configured to use `SPRING_JPA_HIBERNATE_DDL_AUTO=update` inside Docker by default.

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
