# ShopWave Contributor Handbook

This handbook is the tracked source of truth for understanding, running, testing, and changing ShopWave. It is written for new contributors and reflects the current repository rather than unimplemented proposals.

## Start Here

1. [Getting started](getting-started.md) — run the complete stack or a hybrid local setup.
2. [Architecture](architecture.md) — understand service boundaries and data flows.
3. [Domain guide](domain-guide.md) — find the code and APIs for a business capability.
4. [Contributing](../CONTRIBUTING.md) — prepare and verify a change.

## Reference

- [API guide](api-guide.md) — authentication, envelopes, endpoint families, and examples
- [Configuration](configuration.md) — environment variables and external integrations
- [Testing](testing.md) — unit, integration, frontend, browser, and CI workflows
- [Operations](operations.md) — health, logs, migrations, resets, and troubleshooting
- [Candidate roadmap](roadmap.md) — possible future directions, not committed functionality

Subsystem notes live in the [frontend README](../frontend/README.md) and [backend README](../backend/README.md).

## Community

- [Contributing](../CONTRIBUTING.md)
- [Code of Conduct](../CODE_OF_CONDUCT.md)
- [Support](../SUPPORT.md)
- [Security policy](../SECURITY.md)
- [MIT License](../LICENSE)

## Documentation Rules

- Treat source code, `docker-compose.yml`, application configuration, and CI as authoritative.
- Describe planned functionality only in the roadmap and label it as planned or exploratory.
- Never copy secrets or values from a real `.env` file into documentation.
- Update the relevant guide whenever a change affects setup, configuration, architecture, API conventions, tests, or operational behavior.
- Prefer relative links so the handbook works in forks and local clones.

The ignored `docs/` directory contains historical planning material. It may be useful for context, but it is not maintained as part of this handbook.
