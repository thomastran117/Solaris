# ShopWave Design System

This is the reference for building ShopWave frontend UI. Read it before adding a screen, a component or a colour. It describes the code in `src/app/globals.css`, `src/components/ui` and `src/components/theme`; if this page and the code disagree, the code wins and this page needs a fix in the same PR.

## Principles

1. **Neutral first, one accent.** Surfaces are neutral zinc. Blue (`primary`) marks the one most important action in a region and interactive text (`link`). Status colours (success, warning, danger, info) are for status only, never decoration.
2. **Borders before shadows.** Separate surfaces with a 1px `border`. Use `shadow-xs` on cards and `shadow-lg` only on floating layers (menus, dialogs, sheets, toasts).
3. **Opaque surfaces.** No glassmorphism, gradients or glows. The sticky site header is the one place a backdrop blur is allowed.
4. **Content is honest.** Show live data or an explicit empty state. No invented stats, testimonials or placeholder links (`href="#"`).
5. **Every view works in both themes.** If you style with tokens, this is automatic.

## Tokens

All colours are CSS variables in `src/app/globals.css`, defined once for light (`:root`) and once for dark (`.dark`), and exposed to Tailwind through `@theme inline`. Use the Tailwind utility (`bg-surface`, `text-muted-foreground`), not the variable, in class names; use `var(--token)` only where a class can't go (inline styles, chart props, third-party appearance objects).

| Token                                              | Utility examples                                            | Light                       | Dark                | Use for                                                          |
| -------------------------------------------------- | ----------------------------------------------------------- | --------------------------- | ------------------- | ---------------------------------------------------------------- |
| `background`                                       | `bg-background`                                             | `#fafafa`                   | `#09090b`           | Page canvas (set on `body`)                                      |
| `surface`                                          | `bg-surface`                                                | `#ffffff`                   | `#18181b`           | Cards, panels, inputs, table bodies                              |
| `surface-raised`                                   | `bg-surface-raised`                                         | `#ffffff`                   | `#1f1f23`           | Floating layers: menus, dialogs, sheets                          |
| `muted`                                            | `bg-muted`, `hover:bg-muted`                                | `#f4f4f5`                   | `#2a2a2f`           | Hover/selected rows, secondary buttons, skeletons, table headers |
| `foreground`                                       | `text-foreground`                                           | `#09090b`                   | `#fafafa`           | Primary text and icons                                           |
| `muted-foreground`                                 | `text-muted-foreground`                                     | `#52525b`                   | `#a1a1aa`           | Secondary text, captions, placeholders, inactive icons           |
| `border`                                           | `border` (default colour)                                   | `#e4e4e7`                   | `#2e2e33`           | Dividers and surface outlines                                    |
| `input`                                            | `border-input`                                              | `#d4d4d8`                   | `#3f3f46`           | Form control outlines                                            |
| `ring`                                             | `ring-ring`                                                 | `#2563eb`                   | `#3b82f6`           | Focus rings                                                      |
| `primary` / `primary-hover` / `primary-foreground` | `bg-primary text-primary-foreground hover:bg-primary-hover` | blue-600 / blue-700 / white | same                | The main call to action                                          |
| `link`                                             | `text-link`                                                 | `#1d4ed8`                   | `#60a5fa`           | Inline links and text buttons                                    |
| `success`, `warning`, `danger`, `info`             | `text-danger`                                               | 700-weight hues             | 300/400-weight hues | Status text and icons                                            |
| `*-subtle`                                         | `bg-success-subtle`                                         | 50-weight tints             | 950-weight tints    | Badge and alert backgrounds paired with the status text colour   |
| `danger-hover`, `danger-foreground`                | `bg-danger text-danger-foreground`                          |                             |                     | Destructive buttons                                              |
| `overlay`                                          | `bg-overlay`                                                | zinc-950 / 50%              | black / 60%         | Dialog and sheet backdrops                                       |
| `chart-1` … `chart-5`                              | `var(--chart-1)`                                            |                             |                     | Data series in charts (in order)                                 |

Fonts are Geist Sans (`font-sans`, the default) and Geist Mono (`font-mono`), loaded from the `geist` package through `next/font/local`, so builds need no network.

**Contrast is tested.** `src/lib/theme/tokens.test.ts` checks every text/background pair at WCAG AA (4.5:1) and focus rings at 3:1, in both themes. The primary fill stays blue-600 in dark mode because white on blue-500 fails AA.

**Adding a token:** add it to `:root` _and_ `.dark` as a hex value, map it in `@theme inline`, add its pairs to `tokens.test.ts`, and add a row to the table above. Don't add a token for a one-off; reuse the closest existing one.

## Theming

- `src/lib/theme/themeScript.ts` runs inline in `<head>` before first paint. It resolves the theme (forced-dark route → stored choice → OS preference), then sets the `dark` class and `color-scheme` on `<html>`. Nothing flashes.
- `components/theme/ThemeProvider.tsx` keeps that in sync after hydration (storage changes in other tabs, OS changes, client navigation) and exposes `useTheme()` → `{ preference, resolved, forced, setTheme }`.
- `components/theme/ThemeToggle.tsx` is the Light / Dark / System menu used in the headers. The choice is stored in `localStorage` under `shopwave-theme`; "System" removes the key.
- The `dark:` variant is for things tokens can't express: swapping an illustration or a logo, or inverting `prose`. Don't write `text-zinc-900 dark:text-zinc-100`; write `text-foreground`.
- Third-party widgets that can't read CSS variables (Stripe Elements, chart tooltips) take literal values for the resolved theme from `useTheme().resolved`.

### Migration status (temporary)

Views are moving from the legacy navy-glass palette to tokens over several PRs. Until a view migrates it hard-codes dark colours, so `LEGACY_DARK_PREFIXES` in `src/lib/theme/themeConfig.ts` pins those routes to dark and hides the toggle there (`LEGACY_DARK_EXCEPTIONS` carves out migrated paths under a forced prefix, such as `/lists/public`). Today that is only the seller routes (`/admin`, `/dashboard`); everything else follows the visitor's theme.

| Area                                                                          | Status  |
| ----------------------------------------------------------------------------- | ------- |
| Tokens, theme, primitives (`components/ui`, `components/theme`)               | Done    |
| App shell (site header and footer, seller sidebar, not-found and error pages) | Done    |
| Storefront and auth routes (incl. signup and verification)                    | Done    |
| Cart and checkout                                                             | Done    |
| Account routes                                                                | Done    |
| Merchant admin routes                                                         | Pending |

When a group migrates, shrink the forced list, move its folders into the lint `error` block in `eslint.config.mjs`, and update this table. Delete this section, the forced-dark mechanism, `NavyGridGlowBackground` and `components/section/*` when the last group lands.

## Typography

| Role                            | Classes                                                                                |
| ------------------------------- | -------------------------------------------------------------------------------------- |
| Storefront hero / page title    | `text-3xl sm:text-4xl font-semibold tracking-tight` (`SectionHeader as="h1"`)          |
| App page title (`PageHeader`)   | `text-2xl font-semibold tracking-tight`                                                |
| Section title (`SectionHeader`) | `text-2xl sm:text-3xl font-semibold tracking-tight`                                    |
| Card title                      | `text-base font-semibold`                                                              |
| Body                            | `text-base` on storefront reading surfaces, `text-sm` in app UI (forms, tables, menus) |
| Caption / helper                | `text-xs text-muted-foreground`                                                        |
| IDs, SKUs, codes                | `font-mono text-xs`                                                                    |
| Prices                          | `font-semibold tabular-nums`                                                           |

One `<h1>` per page. Don't skip heading levels for visual size; restyle the correct level instead. Use sentence case for headings, buttons and labels.

## Layout

- **Containers:** `Container` (`max-w-7xl`, `px-4 sm:px-6 lg:px-8`) for storefront pages; `size="narrow"` (`max-w-3xl`) for forms, auth and legal text.
- **Shells:** route-group layouts own the chrome; the root layout renders only `<html>`, the theme script, a skip link and providers. `components/layout/SiteShell` (sticky `SiteHeader`, `<main id="main">`, `SiteFooter`) wraps storefront and account routes; account pages add `AccountShell` (sidebar of account links, a tab strip on small screens). Auth routes use `AuthShell` and checkout uses `CheckoutShell`. `components/admin/AdminShell` wraps `/admin/*` and `/dashboard`. Views render content only: never their own `<main>`, header or page background.
- **Admin:** full width, `w-60` sidebar from `lg`, `h-14` top bar; the sidebar becomes a Sheet on smaller screens. Sidebar entries live in `components/admin/adminNav.ts`, gated by the same company capability or platform role the backend checks. Add new admin areas there, and the overview page and sidebar pick them up.
- **Spacing:** Tailwind's 4px scale. Stack page sections with `space-y-12`/`py-12` on the storefront and `space-y-6` in app pages; use `gap-4`/`gap-6` inside grids.
- **Radii:** `rounded-lg` for controls (buttons, inputs, menu items), `rounded-xl` for cards, dialogs and table containers, `rounded-full` for badges and avatars. Don't use `rounded-2xl`/`rounded-3xl`.
- **Grids:** product grids are `grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4 sm:gap-6`.
- **Phone width:** every page must work at 360px with no horizontal page scroll. Wide tables scroll inside their `TableContainer`.

## Icons

`lucide-react` is the only icon set (`react-icons` is banned by lint). Use 16px (`size-4`) inline with text and in buttons, 20px (`size-5`) for standalone icon buttons. Decorative icons get `aria-hidden`; an icon-only control uses `IconButton`, which requires an `aria-label`.

## Components

Everything below lives in `src/components/ui` and is the default way to build UI. Use `cn()` from `src/lib/cn.ts` to merge a caller's `className`; later classes win over defaults.

| Component                                                                                              | Use it for                           | Notes                                                                                                                                                                                                                                 |
| ------------------------------------------------------------------------------------------------------ | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Button`                                                                                               | Every clickable action               | Variants `primary` (one per region), `secondary`, `outline`, `ghost`, `destructive`, `link`; sizes `sm`/`md`/`lg`/`icon`/`icon-sm`. `asChild` styles a `<Link>`. `loading` shows a spinner and disables. Defaults to `type="button"`. |
| `IconButton`                                                                                           | Icon-only actions                    | `aria-label` required by its type.                                                                                                                                                                                                    |
| `Input`, `Textarea`, `Select`                                                                          | Text entry and choices               | `Select` is native for mobile. Always inside a `Field` or with an explicit label.                                                                                                                                                     |
| `Checkbox`, `RadioCard`                                                                                | Booleans; rich single choice         | Native inputs. Group `RadioCard`s in a `<fieldset>` with a `<legend>`.                                                                                                                                                                |
| `Field`, `Label`                                                                                       | Label + control + help + error       | Wires `id`, `aria-invalid`, `aria-describedby`; accepts a React Hook Form `FieldError`.                                                                                                                                               |
| `Card` (+ `CardHeader`, `CardTitle`, `CardDescription`, `CardContent`, `CardFooter`)                   | Grouped content on a surface         | Replaces the old glass-card class strings.                                                                                                                                                                                            |
| `Badge`                                                                                                | Status and counts                    | `neutral`, `primary`, `success`, `warning`, `danger`, `info`, `outline`. Map domain enums to variants in one place (e.g. `OrderStatusBadge`).                                                                                         |
| `Tabs`                                                                                                 | Switching panels in place            | Use controlled when the tab is in the URL (`?tab=`).                                                                                                                                                                                  |
| `DropdownMenu`                                                                                         | Menus of actions or options          | Opens on click/keyboard, never hover.                                                                                                                                                                                                 |
| `Dialog`, `Modal`                                                                                      | Focused tasks                        | `Modal` is the simple controlled wrapper; compose `Dialog*` parts for anything custom. Focus is trapped and restored.                                                                                                                 |
| `Sheet`                                                                                                | Edge drawers                         | Cart, mobile navigation, mobile filters.                                                                                                                                                                                              |
| `ConfirmDialog`                                                                                        | Destructive or irreversible confirms | Replaces `window.confirm`. Keep it open with `loading` until the mutation settles.                                                                                                                                                    |
| `toast` / `Toaster`                                                                                    | Confirming a completed action        | Replaces `window.alert`. Not for validation or load errors.                                                                                                                                                                           |
| `Skeleton`, `Spinner`                                                                                  | Loading                              | Skeletons for content regions; spinners inside buttons or tiny areas.                                                                                                                                                                 |
| `EmptyState`, `ErrorState`                                                                             | No data / failed load                | `ErrorState` takes `onRetry` (usually a query's `refetch`).                                                                                                                                                                           |
| `PageHeader`                                                                                           | Top of account and admin pages       | Title, description, eyebrow (breadcrumb/back link), actions.                                                                                                                                                                          |
| `SectionHeader`                                                                                        | Storefront sections                  | Replaces `SectionTitle`.                                                                                                                                                                                                              |
| `TableContainer`, `Table`, `THead`, `TBody`, `TRow`, `TH`, `TD`, `TableLoadingRows`, `TableMessageRow` | Data lists                           | Styling shells only; fetching, sorting and paging stay in the view.                                                                                                                                                                   |
| `Pagination`                                                                                           | Paged lists                          | Zero-based `page` to match the API.                                                                                                                                                                                                   |
| `Container`, `Separator`, `Prose`                                                                      | Layout and long-form text            | `Prose` styles legal pages and rich descriptions.                                                                                                                                                                                     |

Extract a new shared component when the same pattern appears in a second place. Put generic primitives in `components/ui` and domain pieces in their domain folder (`components/product`, `components/order`, …).

## Patterns

**Page structure.** Storefront: `Container` → `SectionHeader as="h1"` (or a hero) → sections separated by `py-12`. App: `PageHeader` → content in `Card`s or a `TableContainer`.

**Forms.** React Hook Form + a Zod schema in `src/schemas`. One `Field` per control; pass `errors.x` to `error`. Mirror backend validation limits in the schema. Submit with a `primary` `Button` that shows `loading` while the mutation is pending; disable only while pending. Show a server error above the actions as an inline alert, not a toast.

**Data states.** Every query-backed region handles all four: loading (`Skeleton` shaped like the content, or `TableLoadingRows`), empty (`EmptyState` with the next action), error (`ErrorState` with retry), and success.

**Feedback.** Inline errors next to what failed; `toast.success` for completed actions the user might not otherwise see ("Saved to list"); `ConfirmDialog` before destructive actions.

**Money and numbers.** Format with the helpers in `src/lib/format.ts`; use `tabular-nums` where numbers align in columns. Never format currency by string concatenation.

**Links vs buttons.** Navigation is a `<Link>` (styled with `Button asChild` if it should look like a button); actions are `<button>`s.

## Motion

- Page and section entrances use Framer Motion through `useAnims()` (`src/hooks/useAnims.ts`): `fadeInUp`, `fadeIn`, `stagger`, `hoverLift`. It renders content visible before hydration, so server-rendered pages never depend on JavaScript to show text; don't bypass it with your own `initial={{ opacity: 0 }}`.
- Primitives animate with short CSS animations keyed to Radix `data-state` (150–220ms) and turn them off under `motion-reduce`.
- Keep hover motion subtle: colour changes, or `hoverLift` (4px) on cards. No parallax, scroll-linked effects or count-up numbers.
- Always respect reduced motion (`useReducedMotion` / `motion-reduce:`).

## Accessibility

- Text meets WCAG AA in both themes (tested for tokens; keep custom combinations to token pairs).
- Every interactive element shows `focus-visible:ring-2 ring-ring`; primitives do this already. Never remove an outline without replacing it.
- Everything works from the keyboard. Menus and dialogs come from Radix for exactly this reason; don't hand-roll hover menus.
- Every control has a label (visible via `Field`, or `aria-label` on icon buttons). Errors are announced (`role="alert"` in `Field`).
- Use semantic elements: `<header>`, `<nav>`, `<main id="main">`, `<footer>`, real `<button>`s and `<a>`s, `<table>` for tabular data.
- Status changes that happen without navigation (cart count, async results) go in a polite live region.

## Guardrails

`shopwave/no-raw-palette` (`eslint-rules/no-raw-palette.mjs`) flags raw Tailwind palette colours (`text-white`, `bg-slate-950`, `border-sky-400/30`), arbitrary hex colours (`bg-[#0b1221]`), `backdrop-blur` outside the site header, and bare hex strings. `no-restricted-imports` bans `react-icons` and the legacy navy-glass components. Both are errors in migrated folders and warnings elsewhere until the migration completes.

Replacing legacy classes:

| Legacy                                                                                 | Use instead                                 |
| -------------------------------------------------------------------------------------- | ------------------------------------------- |
| `bg-slate-950` page background, `NavyGridGlowBackground`, `SectionGlow`, `SectionFade` | Nothing; `body` already has `bg-background` |
| `text-white`                                                                           | `text-foreground`                           |
| `text-white/40` … `text-white/70`                                                      | `text-muted-foreground`                     |
| `text-sky-*`, `text-blue-400` links                                                    | `text-link`                                 |
| `border-white/*`                                                                       | `border` (default colour)                   |
| `rounded-2xl border border-white/10 bg-white/[0.04] backdrop-blur`                     | `Card`                                      |
| `bg-white/5` … `bg-white/10` hover                                                     | `hover:bg-muted`                            |
| `bg-blue-600 hover:bg-blue-500`                                                        | `Button`                                    |
| Red buttons                                                                            | `Button variant="destructive"`              |
| Coloured pills                                                                         | `Badge variant=…`                           |
| Local `inputCls` / `labelCls` / `panelCls` strings                                     | `Input` / `Field` / `Card`                  |
| `SectionTitle theme="dark"`                                                            | `SectionHeader` or `PageHeader`             |
| `window.alert` / `window.confirm`                                                      | `toast` / `ConfirmDialog`                   |
| Hex chart colours                                                                      | `var(--chart-1)` … `var(--chart-5)`         |

If you truly need an exception, add `// eslint-disable-next-line shopwave/no-raw-palette -- <reason>` on that line.

### UI pull request checklist

- [ ] Uses `components/ui` primitives and semantic tokens; no new lint warnings in touched files.
- [ ] Checked in light **and** dark mode, at phone width and desktop.
- [ ] Loading, empty, error and success states are handled.
- [ ] Works with the keyboard alone; focus is visible; controls are labelled.
- [ ] Respects reduced motion; text is visible without JavaScript on server-rendered pages.
- [ ] Vitest coverage for new component or page logic; a Playwright test or documented manual smoke test for routing and critical journeys.
- [ ] This page is updated if you added a token, primitive or pattern.
