# Contributing to ShopWave

Thank you for improving ShopWave. This repository does not prescribe a branch naming scheme or commit-message format; keep changes focused, explain their intent, and make them independently reviewable.

Participation is governed by the [Code of Conduct](CODE_OF_CONDUCT.md). Use [SUPPORT.md](SUPPORT.md) for help and [SECURITY.md](SECURITY.md) for private vulnerability reporting.

## Before You Start

1. Read the [contributor handbook](documentation/README.md).
2. Run the project using [getting started](documentation/getting-started.md).
3. Use the [domain guide](documentation/domain-guide.md) to identify all affected backend and frontend surfaces.
4. Check the working tree before editing and preserve unrelated changes.

## Issue Naming and Triage

Issue titles follow **`[<GitHub issue number>] <type>: <imperative summary>`**. The bracketed ID is the GitHub issue number, not a sprint number or epic ID. For example:

- `[82] docs: map existing routes to Next.js`
- `[104] feat: make checkout payment retries idempotent`
- `[61] bug: fix catalogue sorting against Elasticsearch`
- `[66] epic: migrate the storefront to Next.js`

Supported types: `epic` (parent roadmap), `feat` (new user-facing capability), `bug` (defect), `refactor` (behaviour-preserving restructuring), `docs`, `test`, `ci`, `chore`, and `perf`. `fix` is also accepted for compatibility, but new defect reports should prefer `bug`.

For a new issue, start the title with the **type and a summary only** (for example, `feat: add saved basket state`). The [issue naming workflow](.github/workflows/issue-labeler.yml) inserts the correct numeric prefix after GitHub assigns the issue number; it also normalizes titles edited later.

Use the existing [feature request](.github/ISSUE_TEMPLATE/feature_request.yml) and [bug report](.github/ISSUE_TEMPLATE/bug_report.yml) forms. Feature tickets should include a problem/opportunity, proposed behaviour, product area, domain, priority, complexity, explicit acceptance criteria, test expectations, dependencies and parent epic where relevant. Bugs should contain reproduction, expected vs actual behaviour, environment, diagnostics and a test confirming the fix. Keep descriptions actionable and moderately detailed rather than broad feature wish lists.

Priority and complexity values are read from the issue form by the GitHub Actions label workflow. Avoid duplicating existing issues, and link implementation tasks to their epic by referencing `#<number>` in the issue body.

## Development Workflow

- Prefer a small change that solves one problem completely.
- Follow existing package, module, naming, and dependency patterns.
- Do not mix generated artifacts, local environment files, or unrelated formatting into the change.
- Update tests and documentation in the same change as behavior or configuration.
- Keep real credentials out of source, examples, tests, screenshots, and logs.

## Backend Changes

- Keep controllers focused on HTTP mapping, validation, and DTO conversion.
- Put business rules and transaction boundaries in services.
- Map entities to response DTOs rather than returning JPA entities.
- Paginate list operations that can grow without a small fixed bound.
- Make retried commands, webhook processing, and Kafka consumers idempotent.
- Preserve consistency for orders, payments, and inventory; do not use Redis as their source of truth.
- Handle provider timeouts and transient failures according to the owning domain's established retry, circuit-breaker, or compensation pattern.

### Schema Changes

Flyway owns the database schema.

1. Add a new migration under `backend/src/main/resources/db/migration`.
2. Use the next version and a descriptive `V<n>__<description>.sql` filename.
3. Never modify a migration that may already have run.
4. Keep `spring.jpa.hibernate.ddl-auto=validate`.
5. Test startup against a clean PostgreSQL database and run affected integration tests.

## Frontend Changes

- Keep API access in `src/api` and shared response/domain types in `src/types`.
- Use TanStack Query for server state and invalidate affected query keys after mutations.
- Use Redux only for appropriate cross-cutting client state.
- Build forms with React Hook Form and Zod.
- Reuse existing layout, section, and domain components.
- Follow the [frontend design system](frontend/DESIGN.md): semantic tokens and shared primitives, working in light and dark mode, with meaningful loading, empty, and error states.
- Respect reduced-motion preferences.
- Avoid `any`; use `unknown` and narrow it when input is not yet typed.

## Tests

Use the [testing guide](documentation/testing.md) to select the right level.

At minimum:

- Add or update a focused test for behavior changes.
- Run the complete unit/component suite for each affected application.
- Run relevant backend `*IT` classes for controller, persistence, security, transaction, or integration behavior.
- Run frontend lint and build for frontend changes.
- Validate Compose configuration for container or environment changes.

If a required check cannot run locally, state which check was skipped and why in the pull request.

## Documentation

Update documentation when a change affects:

- prerequisites, setup commands, ports, or seed data
- architecture or system ownership
- API authentication, envelopes, errors, or endpoint families
- environment variables or provider configuration
- tests, CI, health, migrations, recovery, or troubleshooting
- implemented capability status

Current behavior belongs in the tracked `documentation/` handbook. Aspirational work belongs in [the candidate roadmap](documentation/roadmap.md). Do not rely on the ignored `docs/` planning archive as current documentation.

## Pull Request Readiness

Before requesting review:

- [ ] The change is scoped and the rationale is clear.
- [ ] No secrets, local `.env` files, build output, or unrelated changes are included.
- [ ] New behavior has appropriate tests.
- [ ] Relevant existing tests pass.
- [ ] Frontend lint/build or backend verification passes as applicable.
- [ ] Database changes use a new Flyway migration.
- [ ] API and configuration compatibility have been considered.
- [ ] Documentation reflects the final behavior.
- [ ] Known limitations and skipped checks are recorded.
