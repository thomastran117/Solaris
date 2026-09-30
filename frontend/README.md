# ShopWave Frontend

The frontend is a React 19 and TypeScript application built with Vite. It provides the public marketplace, customer account and order experiences, and merchant/admin workspaces.

For complete project setup, architecture, and operational guidance, start with the [contributor handbook](../documentation/README.md).

## Requirements

- Node.js 20, matching CI
- The ShopWave backend and infrastructure for live API flows

## Local Development

From this directory:

```powershell
npm ci
npm run dev
```

Vite serves <http://localhost:3090> and proxies `/api` to `http://localhost:8090`.

Environment variables are documented in [configuration](../documentation/configuration.md#frontend-build-variables). Copy or create `frontend/.env.local` only when overriding Vite values for a local process; do not put secrets in `VITE_*` variables.

## Commands

| Command                 | Purpose                                     |
| ----------------------- | ------------------------------------------- |
| `npm run dev`           | Start the Vite development server           |
| `npm run build`         | Type-check and create the production bundle |
| `npm run preview`       | Preview a production build                  |
| `npm run lint`          | Run ESLint                                  |
| `npm run format:check`  | Check Prettier formatting                   |
| `npm run format`        | Rewrite files with Prettier                 |
| `npm run test -- --run` | Run Vitest once                             |

## Structure

| Path                | Responsibility                                              |
| ------------------- | ----------------------------------------------------------- |
| `src/pages`         | Route-level screens, including the merchant/admin workspace |
| `src/components`    | Shared and domain components                                |
| `src/api`           | Backend client modules                                      |
| `src/types`         | API and domain TypeScript types                             |
| `src/schemas`       | Zod validation schemas                                      |
| `src/stores`        | Redux Toolkit slices                                        |
| `src/hooks`         | Shared React hooks                                          |
| `src/configuration` | Browser environment handling                                |

`src/App.tsx` is the route map. Server state belongs in TanStack Query; cross-cutting client state belongs in the appropriate Redux slice.

## UI Conventions

- Preserve the established navy glassmorphism visual language used by `HomePage.tsx` and shared layout/section components.
- Reuse `NavyGridGlowBackground`, `SectionTitle`, `SectionGlow`, `SectionFade`, and existing domain components before creating a new pattern.
- Use React Hook Form with Zod for forms.
- Use the shared animation hook and respect reduced-motion preferences.
- Keep API response types in `src/types` and avoid `any`.

See [testing](../documentation/testing.md#frontend-tests) and [contributing](../CONTRIBUTING.md) before submitting a change.
