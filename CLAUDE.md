# CLAUDE.md — ShopWave Repository Guidance

This file records high-impact engineering constraints for AI-assisted work in this repository. Shared setup, architecture, configuration, API, and testing information belongs in the tracked [contributor handbook](documentation/README.md); do not duplicate or contradict it here.

Before changing code, read the relevant handbook page and inspect the current implementation.

## Project Snapshot

- Frontend: React 19, TypeScript, Vite, TanStack Query, Redux Toolkit, React Hook Form, Zod, Tailwind CSS, and Framer Motion
- Backend: Java 21 and Spring Boot 3.4
- Infrastructure: PostgreSQL, Redis, Elasticsearch, and Kafka
- Schema ownership: Flyway; Hibernate validates mappings

Entry points:

- [Architecture](documentation/architecture.md)
- [Domain guide](documentation/domain-guide.md)
- [API guide](documentation/api-guide.md)
- [Configuration](documentation/configuration.md)
- [Testing](documentation/testing.md)
- [Contributing](CONTRIBUTING.md)

## Backend Invariants

### Boundaries

- Controllers handle HTTP mapping and validation, delegate to services, and return DTOs.
- Do not expose JPA entities from REST endpoints.
- Services own business rules and transaction boundaries.
- Use constructor injection.
- Paginate queries whose result can grow without a fixed small bound.

### Orders, Payments, and Inventory

These paths prioritize correctness:

- PostgreSQL is the source of truth.
- Stock mutations must use the established locking/versioning patterns.
- Revalidate availability at order mutation time.
- Preserve idempotency for retried requests, payments, webhooks, and event handling.
- Do not add fire-and-forget work that can silently lose a critical state transition.
- Keep external calls and database commits coordinated through the existing compensation or after-commit patterns.

### Catalog and Supporting Infrastructure

- Redis is a cache and coordination system, never the authoritative inventory store.
- Elasticsearch is a search projection; PostgreSQL remains authoritative.
- Catalog reads may be eventually consistent, but checkout stock validation may not be.
- Kafka consumers must tolerate duplicates and use the configured retry/dead-letter patterns.
- Refer to Kafka topics through application configuration rather than scattering names.

### Database

- Add schema changes as a new `V<n>__<description>.sql` migration.
- Never edit an applied migration, including the baseline.
- Keep `spring.jpa.hibernate.ddl-auto=validate`.
- Avoid vendor-specific `columnDefinition` unless the type cannot be expressed portably.
- Check query plans and association loading when a change could introduce N+1 behavior.

### Security and Errors

- Use the existing JWT, `@RequireAuth`, membership, and role patterns.
- Do not weaken cookie, CORS, captcha, OAuth, rate-limit, or SSRF protections to make a local test pass.
- Do not log access tokens, refresh cookies, OAuth assertions, payment secrets, webhook secrets, or sensitive provider payloads.
- Preserve the common `ApiResponse` envelope and stable error codes.
- Map expected failures to domain-specific exceptions handled by `GlobalExceptionHandler`.

## Frontend Invariants

### Data and Forms

- Use TanStack Query for server state; do not mirror it into component state through effects.
- Put API calls in `src/api` and shared contracts in `src/types`.
- Invalidate the appropriate query keys after successful mutations.
- Use React Hook Form and Zod for forms.
- Avoid `any`; use precise types or `unknown` with narrowing.
- Provide explicit loading, empty, success, and error states.

### Visual Language

`frontend/src/pages/HomePage.tsx` and the shared layout/section components are the current design references.

- Keep page shells in the established navy/dark glassmorphism style.
- Reuse `NavyGridGlowBackground`, `SectionTitle`, `SectionGlow`, `SectionFade`, and existing domain components.
- Use the established blue/sky accents, translucent surfaces, and white text hierarchy before introducing new tokens.
- Extract a reusable component when a new pattern appears in more than one place.

### Motion and Accessibility

- Use Framer Motion through the shared animation patterns.
- Respect `useReducedMotion`.
- Keep hover and entrance motion subtle.
- Preserve keyboard access, focus states, labels, and semantic elements.

## Validation

Use the commands and selection guidance in [testing](documentation/testing.md).

For backend behavior:

- Add focused unit coverage for isolated logic.
- Add or update `*IT` coverage for HTTP, persistence, transactions, security, or provider boundaries.
- Run against a clean migrated database for schema changes.

For frontend behavior:

- Add Vitest/Testing Library coverage for component and page logic.
- Run lint, formatting check, build, and relevant tests.
- Add a browser test or document a manual smoke test for routing and critical journeys.

For configuration or documentation:

- Validate `docker compose --env-file .env.example config`.
- Check all relative documentation links.
- Verify that examples match the current source and do not contain secrets.

## Documentation Ownership

The tracked `documentation/` handbook describes current behavior. Update it with any relevant code change.

The ignored `docs/` directory is a historical planning archive. It is not authoritative and must not be presented as implemented behavior. New speculative ideas belong in [the candidate roadmap](documentation/roadmap.md) until they receive an implementation design and owner.
