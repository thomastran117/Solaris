# Security Policy

## Supported Versions

ShopWave does not currently publish versioned releases. Security maintenance targets the current `main` branch.

| Version                                     | Supported    |
| ------------------------------------------- | ------------ |
| Current `main`                              | Yes          |
| Older commits, forks, and unmerged branches | No guarantee |

## Reporting a Vulnerability

Do not disclose a suspected vulnerability in a public issue, pull request, discussion, screenshot, or log.

Submit a private report through the repository's [GitHub Security Advisory form](https://github.com/thomastran117/E-commerce/security/advisories/new). Include:

- The affected component and code path
- The vulnerability type and likely impact
- Reproduction steps or a minimal proof of concept
- Preconditions, configuration, and environment details
- Any known mitigation or suggested fix
- Whether the issue has been disclosed anywhere else

Remove real credentials, access tokens, customer data, payment data, and unrelated personal information. Use synthetic test data wherever possible.

If private vulnerability reporting is unavailable, use the contact information on the [maintainer's GitHub profile](https://github.com/thomastran117) to request a private channel. Do not include vulnerability details in a public request.

## What to Expect

Maintainers will confirm receipt as availability permits, assess scope and severity, and coordinate remediation and disclosure with the reporter. The project does not currently promise a response or fix SLA.

Please allow time for a fix and affected-user guidance before public disclosure. Maintainers may request additional information or decline reports that do not describe a security impact.

## Scope

Useful reports include vulnerabilities in:

- Authentication, authorization, session, MFA, or account recovery
- Checkout, payment, refund, webhook, or idempotency handling
- Company, vendor, support, or administrator privilege boundaries
- File upload, presigned URL, rich-text, or CSV import processing
- SSRF, injection, deserialization, path traversal, or sensitive-data exposure
- Dependency or configuration behavior that is exploitable in ShopWave

General bugs, feature requests, missing hardening without an exploit path, and findings that require access already equivalent to the claimed impact should use [SUPPORT.md](SUPPORT.md) instead.

## Safe Research

- Test only systems and data you own or are explicitly authorized to use.
- Prefer a local ShopWave environment and seeded development accounts.
- Do not access, alter, retain, or exfiltrate other people's data.
- Avoid denial of service, spam, social engineering, and destructive testing.
- Stop testing and report immediately if you encounter real user data or cause service instability.

This policy does not authorize testing against third-party providers used by ShopWave, including Stripe, OAuth providers, carriers, messaging providers, or storage services. Follow each provider's own security policy.

## Security Guidance for Deployers

Before deploying, replace every development credential, use HTTPS, enable secure cookies, restrict CORS, configure provider secrets through a secret manager, and review [configuration](documentation/configuration.md) and [operations](documentation/operations.md). The checked-in environment template is not a production security baseline.
