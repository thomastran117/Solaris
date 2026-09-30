# ShopWave Backend

The backend is a Java 21 and Spring Boot 3.4 modular monolith. It exposes the `/api` REST surface and owns commerce data, transactions, security, integrations, background jobs, and events.

For complete setup and system context, start with the [contributor handbook](../documentation/README.md).

## Requirements

- Java 21
- Docker for PostgreSQL, Redis, Elasticsearch, Kafka, and integration tests
- Maven is not required globally; use the included wrapper

## Local Development

From the repository root, start infrastructure:

```powershell
docker compose up -d postgres redis elasticsearch kafka
```

Then configure the host-facing database and Kafka ports and run from this directory:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5433/shopwave"
$env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9093"
.\mvnw.cmd spring-boot:run
```

The API listens at <http://localhost:8090/api>; verify it with <http://localhost:8090/api/health>.

See [getting started](../documentation/getting-started.md#hybrid-local-development) and [configuration](../documentation/configuration.md) for Redis, Elasticsearch, JWT, and optional provider settings.

## Commands

```powershell
# Unit suite and coverage gate
.\mvnw.cmd verify "-Dtest=**/*Test" "-Dspring-boot.repackage.skip=true"

# One unit test
.\mvnw.cmd test "-Dtest=MoneyUtilTest"

# One Testcontainers integration test
.\mvnw.cmd test "-Dtest=OrderIT" "-Djacoco.skip=true"

# Package
.\mvnw.cmd package
```

On macOS or Linux, use `./mvnw` in place of `.\mvnw.cmd`.

## Structure

| Path                                | Responsibility                                            |
| ----------------------------------- | --------------------------------------------------------- |
| `controllers/impl`                  | REST endpoint groups                                      |
| `services/intf` and `services/impl` | Business contracts and implementation                     |
| `repositories`                      | JPA and search persistence                                |
| `models`                            | JPA entities and domain enums                             |
| `dtos`                              | Request and response contracts                            |
| `configurations`                    | Security, environment, resilience, and application wiring |
| `events`                            | Kafka contracts, producers, and consumers                 |
| `seeds`                             | Development-profile sample data                           |
| `src/main/resources/db/migration`   | Flyway schema history                                     |

## Invariants

- Controllers use DTOs and delegate to services; do not expose JPA entities.
- Mutating service operations own transaction boundaries.
- PostgreSQL is authoritative for orders and inventory.
- Redis and Elasticsearch are supporting read/coordination systems.
- Kafka consumers and retried operations must be idempotent.
- Flyway owns the schema and Hibernate remains on `ddl-auto=validate`.
- Applied migrations are immutable.

See the [API guide](../documentation/api-guide.md), [architecture](../documentation/architecture.md), and [testing guide](../documentation/testing.md) for more detail.
