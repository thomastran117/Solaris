# ShopWave Frontend

The frontend is a Next.js (App Router) application written in React 19 and TypeScript. It provides the public marketplace, customer account and order experiences, and merchant/admin workspaces. See [ADR 0001](../documentation/decisions/0001-nextjs-app-router.md) for why it uses Next.js and how the migration from Vite is sequenced.

For complete project setup, architecture, and operational guidance, start with the [contributor handbook](../documentation/README.md).

## Requirements

- Node.js 20.9 or newer (CI and the Docker image use Node 24)
- The ShopWave backend and infrastructure for live API flows

## Local Development

From this directory:

```powershell
npm ci
npm run dev
```

The development server listens on <http://localhost:3090> and forwards `/api` to `http://localhost:8090` (override with `API_INTERNAL_URL`).

Environment variables are documented in [configuration](../documentation/configuration.md#frontend-build-variables). Put local overrides, such as `DEFAULT_MARKETPLACE_ID`, in `frontend/.env.local`, which Git ignores. Never put secrets in `NEXT_PUBLIC_*` variables; they are compiled into the browser bundle.

## Commands

| Command                 | Purpose                                                                                                                               |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `npm run dev`           | Start the Next.js development server on port 3090                                                                                     |
| `npm run build`         | Type-check and create the production build (`next build`)                                                                             |
| `npm run start`         | Serve a production build on port 3000 (local use; it prints a standalone warning, and the Docker image runs `node server.js` instead) |
| `npm run lint`          | Run ESLint (`eslint-config-next`)                                                                                                     |
| `npm run typecheck`     | Run `tsc --noEmit`; run `npx next typegen` first if needed                                                                            |
| `npm run format:check`  | Check Prettier formatting                                                                                                             |
| `npm run format`        | Rewrite files with Prettier                                                                                                           |
| `npm run test -- --run` | Run Vitest once                                                                                                                       |

## Structure

| Path                | Responsibility                                                                                                         |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `src/app`           | App Router segments, layouts, providers, and the 404 page; see the [route map](../documentation/frontend-route-map.md) |
| `src/views`         | Route-level client screens rendered by `src/app/**/page.tsx`, including the merchant/admin workspace                   |
| `src/components`    | Shared and domain components; `components/navigation` holds `NavLink` and `useSetSearchParams`                         |
| `src/api`           | Backend client modules, session refresh, and the query-client factory                                                  |
| `src/types`         | API and domain TypeScript types                                                                                        |
| `src/schemas`       | Zod validation schemas                                                                                                 |
| `src/stores`        | Redux Toolkit slices, `makeStore()`, and typed hooks                                                                   |
| `src/hooks`         | Shared React hooks                                                                                                     |
| `src/configuration` | Public environment (`Environment.ts`) and server-only settings (`server.ts`)                                           |
| `src/test`          | Vitest setup and the `next/navigation` / `next/link` test doubles                                                      |

Do not recreate `src/pages`: Next.js treats that directory as Pages Router routes.

Server state belongs in TanStack Query; cross-cutting client state belongs in the appropriate Redux slice. Components must not read `window`, `document`, or storage during render; do it in effects or event handlers, because client components are also rendered on the server.

## UI Conventions

[DESIGN.md](DESIGN.md) is the design system reference. In short:

- Style with semantic tokens (`bg-surface`, `text-foreground`, `text-muted-foreground`, `border`, `bg-primary`); every view must work in light and dark mode. The theme is set before first paint by `src/lib/theme/themeScript.ts` and managed by `components/theme/ThemeProvider`.
- Build from `src/components/ui` primitives; use `cn()` from `src/lib/cn.ts` to merge class names.
- `shopwave/no-raw-palette` (in `eslint-rules/`) flags hard-coded colours and fails lint.
- Use React Hook Form with Zod for forms.
- Use the shared animation hook and respect reduced-motion preferences.
- Keep API response types in `src/types` and avoid `any`.

See [testing](../documentation/testing.md#frontend-tests) and [contributing](../CONTRIBUTING.md) before submitting a change.
