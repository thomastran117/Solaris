# Configuration

ShopWave reads backend settings from environment variables referenced by `backend/src/main/resources/application.properties`. Docker Compose passes the local subset from the repository-root `.env` file. Browser-visible frontend variables are compiled into the Next.js build and must start with `NEXT_PUBLIC_`.

Copy `.env.example` to `.env` for local Docker use. The real `.env` is ignored by Git.

## Configuration Principles

- Never commit real credentials, tokens, private keys, webhook secrets, or production URLs.
- Frontend `NEXT_PUBLIC_*` values are public after build; never place a secret in them.
- Keep production secrets in the deployment platform's secret manager.
- Explicitly set `SPRING_PROFILES_ACTIVE` and all security-sensitive values outside local development.
- The values in `.env.example` are development defaults, not a production baseline.

## Core Runtime Variables

| Variable                                            | Purpose                                          | Local template                                                         |
| --------------------------------------------------- | ------------------------------------------------ | ---------------------------------------------------------------------- |
| `SPRING_PROFILES_ACTIVE`                            | Activates backend profile; code default is `dev` | `dev`                                                                  |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER` | Backend PostgreSQL connection                    | Compose injects container values                                       |
| `JWT_SECRET`                                        | HS512 signing secret                             | Insecure local placeholder                                             |
| `JWT_ACCESS_TTL_SECONDS`                            | Access-token lifetime                            | `900`                                                                  |
| `JWT_REFRESH_TTL_SECONDS`                           | Refresh-token lifetime                           | `604800`                                                               |
| `JWT_ISSUER`                                        | Expected token issuer                            | `shopwave-api`                                                         |
| `COOKIE_SECURE`, `COOKIE_SAME_SITE`                 | Refresh-cookie policy                            | `false`/`Lax` for local HTTP; application defaults are `true`/`Strict` |
| `CORS_ALLOWED_ORIGINS`                              | Comma-separated browser origins                  | Local frontend origins                                                 |
| `REDIS_HOST`, `REDIS_PORT`                          | Redis connection                                 | Compose injects container values                                       |
| `ES_HOST`, `ES_PORT`, `ES_SCHEME`                   | Elasticsearch connection                         | Compose injects container values                                       |
| `KAFKA_BOOTSTRAP_SERVERS`                           | Kafka broker list                                | `kafka:9092` in Compose                                                |
| `OAUTH_VALIDATE_CONFIG_AT_STARTUP`                  | Fail startup when OAuth config is invalid        | `false` locally                                                        |

`JWT_SECRET` must resolve to enough key material for HS512. The checked-in value only allows the local stack to start and must be replaced in every shared or deployed environment.

## Compose Ports

The template deliberately chooses host ports that reduce collisions:

| Variable        | Template host port | Container port | Compose fallback without `.env` |
| --------------- | ------------------ | -------------- | ------------------------------- |
| `FRONTEND_PORT` | `3000`             | `80`           | `3000`                          |
| `BACKEND_PORT`  | `8090`             | `8090`         | `8090`                          |
| `POSTGRES_PORT` | `5433`             | `5432`         | `5433`                          |
| `REDIS_PORT`    | `6379`             | `6379`         | `6380`                          |
| `ES_PORT`       | `9200`             | `9200`         | `9201`                          |
| `KAFKA_PORT`    | `9093`             | `9092`         | `9093`                          |

Container-to-container traffic always uses the container ports and service names. Host ports matter to tools and applications running outside Compose.

## Frontend Build Variables

| Variable                         | Purpose                                                                                                   |
| -------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `NEXT_PUBLIC_BACKEND_URL`        | REST base path; the default `/api` goes through the Next.js rewrite                                       |
| `NEXT_PUBLIC_BACKEND_WS_URL`     | Optional WebSocket base URL including `/api`; otherwise derived from the browser origin plus `/api`       |
| `NEXT_PUBLIC_FRONTEND_URL`       | Canonical frontend URL used for OAuth redirect URIs and metadata; otherwise the browser origin            |
| `NEXT_PUBLIC_GOOGLE_CLIENT_ID`   | Public Google OAuth client ID                                                                             |
| `NEXT_PUBLIC_MSAL_CLIENT_ID`     | Public Microsoft application ID                                                                           |
| `NEXT_PUBLIC_MSAL_AUTHORITY`     | Microsoft authority URL                                                                                   |
| `NEXT_PUBLIC_RECAPTCHA_SITE_KEY` | Public reCAPTCHA site key                                                                                 |

Changing these values requires rebuilding the frontend container. For a local `npm run dev` process, put overrides in `frontend/.env.local`, which Git ignores.

## Frontend Server Variables

These values are read by the Next.js server and are never sent to the browser. In Docker Compose, both are passed to the frontend image as build arguments and set on the container.

| Variable                 | Default                 | Purpose                                                                                                                                                                                      |
| ------------------------ | ----------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `API_INTERNAL_URL`       | `http://localhost:8090` | Backend origin the `/api/*` rewrite forwards to. Compose uses `http://backend:8090`. Rewrites are resolved when `next build` runs, so this is a Docker build argument as well as a runtime variable. |
| `DEFAULT_MARKETPLACE_ID` | unset                   | UUID of the marketplace whose catalog the storefront shows. Without it, catalog pages show a "no marketplace" state. Pages prerendered at build time read it during the build, so set it at build time too. |
| `STRIPE_PUBLISHABLE_KEY` | unset                   | Stripe **publishable** key for Payment Element at `/checkout` and on unpaid orders. Read per request and passed to the page, so it is a runtime variable only (no rebuild). Without it, checkout still reserves the order and shows "Payments aren't configured". `NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY` is accepted as a fallback. |

`NEXT_PUBLIC_DEFAULT_MARKETPLACE_ID` is accepted as a fallback for `DEFAULT_MARKETPLACE_ID`. The value is generated randomly when the development seed data is loaded, so look it up after the backend has seeded the database:

```sql
SELECT id FROM marketplace_profiles WHERE slug = 'techgadgets';
```

## Optional Integrations

Blank values disable or limit the corresponding local feature; they do not provide a mock provider.

| Integration           | Variables                                                                                                |
| --------------------- | -------------------------------------------------------------------------------------------------------- |
| Google OAuth          | `GOOGLE_CLIENT_ID`                                                                                       |
| Microsoft OAuth       | `MICROSOFT_CLIENT_ID`, `MICROSOFT_JWKS_URI`                                                              |
| Apple OAuth           | `APPLE_CLIENT_ID`, `APPLE_JWKS_URI`                                                                      |
| reCAPTCHA             | `RECAPTCHA_SECRET_KEY` and `NEXT_PUBLIC_RECAPTCHA_SITE_KEY`; both are required for login and signup against a real backend, because verification fails closed                                                     |
| Stripe orders         | `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY` (also passed to the frontend container), `STRIPE_WEBHOOK_SECRET` |
| Stripe premium        | `STRIPE_PREMIUM_PRICE_ID`, `STRIPE_PREMIUM_WEBHOOK_SECRET`, premium success/cancel/portal URLs           |
| S3-compatible uploads | `AWS_S3_REGION`, `AWS_S3_BUCKET`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_S3_PUBLIC_URL_BASE` |
| SMTP                  | `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `SUPPORT_TEAM_EMAIL`                                      |
| EasyPost              | `EASYPOST_API_KEY` plus fallback rate and timeout settings                                               |
| AfterShip             | `AFTERSHIP_API_KEY`, `AFTERSHIP_BASE_URL`, `AFTERSHIP_WEBHOOK_SECRET`                                    |
| Firebase push         | `FIREBASE_CREDENTIALS_PATH`                                                                              |
| Twilio SMS            | `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER`                                          |
| Outbound webhooks     | `WEBHOOK_SECRET_KEY` and `WEBHOOK_SSRF_CHECK_ENABLED`                                                    |

## Database and Persistence Tuning

| Variables                                                                                                 | Purpose                                 |
| --------------------------------------------------------------------------------------------------------- | --------------------------------------- |
| `FLYWAY_ENABLED`, `SPRING_JPA_HIBERNATE_DDL_AUTO`                                                         | Migration and mapping-validation policy |
| `DB_POOL_SIZE`, `DB_POOL_MIN_IDLE`                                                                        | Hikari pool sizing                      |
| `DB_CONNECTION_TIMEOUT`, `DB_IDLE_TIMEOUT`                                                                | Connection timing                       |
| `REPO_RETRY_MAX_ATTEMPTS`, `REPO_RETRY_INITIAL_MS`, `REPO_RETRY_MULTIPLIER`, `REPO_RETRY_MAX_INTERVAL_MS` | Read-side repository retry policy       |

Keep `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`. Flyway is the only schema owner.

## Authentication and Security Tuning

| Variables                                                                                | Purpose                                                      |
| ---------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| `BCRYPT_STRENGTH`                                                                        | Password hashing cost; lower values are only for local speed |
| `AUTH_*_PER_IP_LIMIT`, `AUTH_LOGIN_PER_EMAIL_LIMIT`                                      | Endpoint rate-limit buckets                                  |
| `AUTH_RATE_WINDOW_SECONDS`                                                               | Fixed-window duration                                        |
| `AUTH_LOCKOUT_THRESHOLD`, `AUTH_LOCKOUT_WINDOW_SECONDS`, `AUTH_LOCKOUT_DURATION_SECONDS` | Credential failure lockout                                   |
| `DEVICE_VERIFICATION_TTL_SECONDS`                                                        | Device verification lifetime                                 |
| `OAUTH_RETRY_*`, `OAUTH_CB_*`                                                            | OAuth retry and circuit-breaker policy                       |
| `RECAPTCHA_CONNECT_TIMEOUT_MS`, `RECAPTCHA_READ_TIMEOUT_MS`, `RECAPTCHA_RETRY_*`         | Server-side captcha resilience                               |

## Cache, Inventory, and Scheduling

| Variables                                                                   | Purpose                                            |
| --------------------------------------------------------------------------- | -------------------------------------------------- |
| `APP_CACHE_NAMESPACE`                                                       | Prefix separating Redis key spaces                 |
| `REDIS_PASSWORD`, `REDIS_DATABASE`, `REDIS_TIMEOUT`                         | Redis authentication and client behavior           |
| `LOCK_TTL_SECONDS`                                                          | Distributed lock lifetime for order/inventory work |
| `ORDER_RESERVATION_TTL_SECONDS`, `ORDER_STALE_MINUTES`                      | Checkout stock hold and compensation timing        |
| `PRODUCT_CACHE_TTL_SECONDS`, `PRODUCT_CACHE_TTL_SHORT_SECONDS`              | Product cache lifetimes                            |
| `CACHE_EARLY_REFRESH_*`, `CACHE_REFRESH_EXECUTOR_*`                         | Probabilistic refresh and worker sizing            |
| `COUPON_EXPIRY_INTERVAL_MS`, `PRODUCT_SCHEDULING_INTERVAL_MS`               | Scheduled promotion/product tasks                  |
| `DEMAND_REFRESH_INTERVAL_MS`, `DEMAND_CACHE_TTL_1H`, `DEMAND_CACHE_TTL_24H` | Demand rankings                                    |
| `SUB_REMINDER_CRON`, `SUB_DEFAULT_DISCOUNT`                                 | Subscription reminders and defaults                |

`ORDER_STALE_MINUTES` must remain shorter than the reservation TTL so compensation occurs before reservation keys expire.

## Search and Messaging

| Variables                                                                          | Purpose                              |
| ---------------------------------------------------------------------------------- | ------------------------------------ |
| `ES_API_KEY` or `ES_USERNAME`/`ES_PASSWORD`                                        | Secured Elasticsearch authentication |
| `ES_INDEXING_WORKER_COUNT`, `ES_INDEXING_QUEUE_CAPACITY`, `ES_INDEXING_BATCH_SIZE` | Indexing throughput                  |
| `ES_RETRY_INTERVAL_MS`                                                             | Failed indexing retry schedule       |
| `ES_FULL_REINDEX_CRON`                                                             | Optional scheduled full reindex      |
| `ES_VERSIONING_ENABLED`                                                            | Versioned-index alias strategy       |
| `ACTIVITY_TRACKING_ENABLED`                                                        | Global activity publishing switch    |

Kafka serializers, consumers, and topic names are defined in `application.properties`. Change topic configuration together with every producer, consumer, and operational deployment that relies on it.

## Commerce and Risk Tuning

| Variables                                                                                      | Purpose                                             |
| ---------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| `STRIPE_RETRY_*`                                                                               | Payment retry/backoff                               |
| `EASYPOST_DEFAULT_WEIGHT_GRAMS`, `EASYPOST_FLAT_RATE_CENTS`, `EASYPOST_FLAT_RATE_SERVICE_NAME` | Local shipping fallback                             |
| `EASYPOST_RATE_CACHE_TTL_SECONDS`, timeout and retry variables                                 | Shipping provider resilience                        |
| `TAX_DEFAULT_RATE`, `TAX_FALLBACK_SHIPPING_TAXABLE`                                            | Checkout-only tax fallback                          |
| `RISK_ENABLED`, `RISK_MODE`                                                                    | Fraud engine switch and `SHADOW`/`ENFORCE` behavior |
| `RISK_VERIFY_THRESHOLD`, `RISK_BLOCK_THRESHOLD`, `RISK_VIP_SEGMENT_ID`                         | Top-level risk policy                               |
| `RISK_FP_*`, `RISK_DEV_*`, `RISK_ACCT_NEW_MIN`                                                 | Payment, device, and account-age signals            |
| `RISK_COUPON_*`, `RISK_RET_*`                                                                  | Coupon and return-pattern signals                   |
| `REVIEW_AUTO_HIDE_THRESHOLD`                                                                   | Moderation report threshold                         |
| `ANNOUNCEMENT_RATE_LIMIT_PER_HOUR`                                                             | Announcement publishing limit                       |

Run the risk engine in `SHADOW` until thresholds have been evaluated with representative traffic.

## Email and Worker Tuning

Email sender identity, verification URL, token lifetime, executor sizing, and retry values use `MAIL_FROM`, `SUPPORT_TEAM_EMAIL`, `EMAIL_VERIFICATION_BASE_URL`, `EMAIL_VERIFICATION_TTL_SECONDS`, `EMAIL_EXECUTOR_*`, and `EMAIL_RETRY_*`. The underlying SMTP host and port are currently configured for Gmail in `application.properties`.

When adding a variable:

1. Give it a safe application default where possible.
2. Add it to Compose only if a container needs an override.
3. Add common developer inputs to `.env.example`.
4. Document its purpose and security properties here.
5. Add configuration validation or tests when an invalid value could cause data loss or insecure behavior.
