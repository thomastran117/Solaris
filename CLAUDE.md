# CLAUDE.md — E-Commerce App

This file guides Claude's behaviour when working in this codebase. Read it fully before making any changes.

---

## Project Overview

A full-stack e-commerce application.

- **Frontend**: React 18, TypeScript, Vite
- **Backend**: Java 21, Spring Boot
- **Messaging**: Apache Kafka
- **Cache**: Redis
- **Database**: MySQL + Hibernate (JPA)

---

## Architecture Priorities

### Backend — Consistency vs. Availability Trade-offs

This project applies different consistency/availability strategies depending on the domain:

#### Orders & Stock — Prioritise Consistency

Orders and stock levels are **strongly consistent**. Never sacrifice correctness for speed here.

- **Stock mutations** (reserve, release, deduct) must go through **pessimistic or optimistic locking** via Hibernate. Prefer `@Version` for optimistic locking on `Product` entities; fall back to `SELECT ... FOR UPDATE` (pessimistic) only when contention is genuinely high.
- **Order creation** is a single atomic transaction. The flow is: validate → reserve stock → persist order → publish Kafka event. If any step fails, the whole transaction rolls back.
- **Kafka events for orders** (e.g. `order.created`, `order.cancelled`) are published **after** the DB transaction commits — never inside it. Use a transactional outbox pattern or `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- **Idempotency keys** must be validated before processing any order mutation. Duplicate requests must be detected and short-circuited cleanly.
- Do not use `@Async` or fire-and-forget patterns for anything that mutates order or stock state.
- Redis must **not** be the source of truth for stock levels. It is a read cache only. Stock writes always go to MySQL first.

#### Products — Prioritise Availability

Product catalogue reads are **eventually consistent** and optimised for availability.

- Product reads should be served from **Redis cache** wherever possible. Cache-aside pattern: check Redis → on miss, load from DB and populate cache.
- Cache TTL for products: **5 minutes** by default. Configure per entity type if needed.
- Product writes (create, update, delete) must **invalidate** the relevant Redis keys immediately after committing.
- It is acceptable for a product page to briefly show slightly stale data (price, description). It is **not** acceptable for stock availability to be stale at checkout time — always revalidate stock against MySQL at the point of order creation.
- Product listing queries should use pagination. Never load unbounded result sets.

---

## Backend Conventions

### General

- Java 21. Use records, sealed interfaces, pattern matching, and text blocks where they improve clarity.
- Spring Boot 3.x conventions. Use constructor injection, not `@Autowired` on fields.
- All service-layer methods that mutate state must be annotated with `@Transactional`. Read-only methods use `@Transactional(readOnly = true)`.
- Never expose JPA entities directly from REST controllers. Always map to DTOs. Use a dedicated mapper class or static factory method.
- Checked exceptions should be caught at the service boundary and re-thrown as domain-specific unchecked exceptions (e.g. `OutOfStockException`, `OrderNotFoundException`).

### Naming

| Layer | Convention | Example |
|---|---|---|
| Entity | `PascalCase`, no suffix | `Order`, `Product` |
| Repository | `<Entity>Repository` | `OrderRepository` |
| Service interface | `<Domain>Service` | `OrderService` |
| Service impl | `<Domain>ServiceImpl` | `OrderServiceImpl` |
| Controller | `<Domain>Controller` | `OrderController` |
| DTO (request) | `<Action><Domain>Request` | `CreateOrderRequest` |
| DTO (response) | `<Domain>Response` | `OrderResponse` |
| Kafka event | `<Domain><Action>Event` | `OrderCreatedEvent` |

### Kafka

- Topic names use `kebab-case`: `order-created`, `stock-reserved`, `order-cancelled`.
- Consumers must be **idempotent**. Always check whether an event has already been processed before applying side effects.
- Use a dead-letter topic (`<topic>.dlq`) for messages that fail after the configured retry limit.
- Consumer group IDs follow the pattern `<service>-<topic>-consumer`.
- Do not use Kafka as an RPC mechanism. Events are facts about things that happened, not commands.

### Redis

- Key naming convention: `<entity>:<id>` (e.g. `product:42`, `products:list:page:1`).
- All Redis operations must be wrapped in try/catch. A Redis failure must **never** cause a write path to fail — degrade gracefully by falling through to the database.
- Do not store sensitive data (e.g. payment details) in Redis.

### Database / Hibernate

- All entities must have a `@Version` field for optimistic locking unless there is an explicit documented reason not to.
- Use `@CreationTimestamp` and `@UpdateTimestamp` on all entities.
- Avoid N+1 queries. Use `JOIN FETCH` or `@EntityGraph` for associations that are always needed together.
- Database migrations are managed with **Flyway**. Never alter a committed migration script — always add a new one.
- Use `snake_case` for all column and table names. Configure `SpringPhysicalNamingStrategy` or equivalent.

---

## Frontend Conventions

### Design Reference

`src/pages/Home.tsx` is the **canonical design reference**. Every new page and component must be visually consistent with it. Before building anything new, read that file. When in doubt, match what it does.

---

### Visual Language

The design is a **navy dark glassmorphism** style. The rules below are derived directly from `Home.tsx` and must be followed exactly.

#### Backgrounds

The root page background is always `bg-slate-950`. Never use white, light grey, or any non-dark background on a page shell.

The global background layer (`NavyGridGlowBackground`) renders:
- A solid `slate-950` base
- Soft, heavily-blurred radial glows in `blue-600/14`, `sky-400/10`, and `indigo-500/10` — never sharp or vivid
- A subtle dot/grid overlay masked with a radial gradient (`opacity-[0.22]`)
- A micro blue grid texture at `14px` spacing (`opacity-[0.08]`)
- A vignette last to add contrast and prevent glare

This component is global. Do not add competing full-page background effects inside individual sections.

#### Section backgrounds

Alternating sections use `bg-white/[0.04] backdrop-blur` with `border-y border-white/10` to visually separate them from open sections — matching the rhythm in `Home.tsx`. Do not use solid dark colours for section backgrounds.

#### Cards and panels

All cards, panels, and tiles use:
```
rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm
```
On hover: `hover:shadow-md`. This is the single card surface pattern. Do not invent alternatives.

For more prominent panels (e.g. the CTA card in `Home.tsx`): use `rounded-3xl` and increase padding to `p-8`.

#### Glows and section depth

Each section uses a `SectionGlow` variant (`a`, `b`, or `c`) and a `SectionFade` for top/bottom blending. These are already extracted as reusable components. Always use them — do not add raw `div` blobs inside sections.

---

### Colour Usage

All colours come from Tailwind utility classes. The effective palette used across the app is:

| Role | Class(es) | Notes |
|---|---|---|
| Page background | `bg-slate-950` | Never deviate |
| Surface (cards) | `bg-white/[0.06]` | With `backdrop-blur` |
| Elevated surface | `bg-white/[0.04]` | Sections, strips |
| Border | `border-white/10` | Default border everywhere |
| Strong border | `border-white/20` | Hover, focus rings |
| Primary CTA | `bg-blue-600 hover:bg-blue-500` | Pill shape (`rounded-full`) |
| Ghost CTA | `border border-white/20 hover:bg-white/10` | No background fill |
| Icon container | `bg-blue-500/15 border border-white/10 rounded-xl` | — |
| Heading text | `text-white` | — |
| Body text | `text-white/70` or `text-white/80` | — |
| Muted / meta text | `text-white/60` or `text-white/65` | Labels, captions |
| Placeholder text | `placeholder:text-white/45` | Inputs |
| Kicker labels | `text-sky-200/90` | Uppercase, `tracking-[0.25em]` |
| Icon colour | `text-sky-200` | All feature/service icons |
| Gradient text | `text-transparent bg-clip-text bg-gradient-to-r from-sky-200 to-blue-400` | Hero accent only |
| Rating stars | `text-blue-400 fill-blue-400` | Active; `text-white/25` inactive |
| Semantic success | `text-green-400` / `bg-green-500` | — |
| Semantic warning | `text-yellow-400` / `bg-yellow-500` | — |
| Semantic error | `text-red-400` / `bg-red-500` | — |

Never introduce new colour values outside of this table without explicit discussion.

---

### Typography

- Headings (`h1`): `text-4xl md:text-6xl font-extrabold tracking-tight`
- Section titles (`h2`): `text-3xl md:text-4xl font-extrabold`
- Card titles (`h3`): `text-lg font-semibold`
- Kicker labels: `text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90`
- Body text: `text-sm` or `text-lg`, `leading-relaxed`, `text-white/70`
- Prices: `text-lg font-extrabold text-white`
- Stat numbers: `text-3xl md:text-4xl font-extrabold text-white`

---

### Reusable UI Components

These components are already extracted in `Home.tsx` and must be promoted to `src/components/` and reused across the app. Do not re-implement them inline.

| Component | What it does |
|---|---|
| `SectionTitle` | Kicker + `h2` + subtitle block. Props: `kicker`, `title`, `subtitle`, `align`, `theme`. |
| `SectionGlow` | Per-section ambient glow blobs. Prop: `variant` (`"a" \| "b" \| "c"`). |
| `SectionFade` | Top/bottom gradient fades for section blending. Props: `top`, `bottom`. |
| `NavyGridGlowBackground` | Fixed full-page background. Render once in the root layout. |
| `ScrollProgressBar` | Fixed top progress bar using `useScroll` + `useSpring`. |
| `FeatureCard` | Icon + title + description card. Props: `Icon`, `title`, `description`. |
| `ProductCard` | Product tile with image placeholder, tag, rating, price. |
| `CategoryTile` | Icon + label navigation tile. |
| `Testimonial` | Blockquote + author figcaption. |
| `RatingStars` | 5-star display from a `rating: number`. |
| `Pill` | Small bordered pill label for hero metadata. |
| `Stat` | Animated count-up stat with label. |
| `CountUp` | Raw count-up animation hook (used inside `Stat`). |

When building new pages, compose from these. If a new pattern is needed more than once, extract it here.

---

### Animation

Motion library is **Framer Motion**. All animations must respect `useReducedMotion`.

The `useAnims()` hook (from `Home.tsx`) returns three standard variants: `fadeInUp`, `fadeIn`, and `stagger`. Always use these for section/card entrance animations — do not write one-off `initial`/`animate` objects inline unless the motion is unique to a single element.

Standard animation rules:
- Section content: `whileInView` with `viewport={{ once: true, amount: 0.25 }}` — not `useEffect` + state.
- Card grids: wrap in a `stagger` parent, children use `fadeInUp`.
- Card hover: `whileHover={{ y: -4 }}` or `whileHover={{ scale: 1.015, translateY: -3 }}` — subtle only.
- Parallax: `useScroll` + `useTransform` for hero layers. Keep `y` range small (max ±90px).
- Spring config for progress bars: `stiffness: 120, damping: 24, mass: 0.2`.
- Do not use `animate` on mount without `whileInView` — it fires before the element is visible.

---

### Component Structure

| Category | Path | Examples |
|---|---|---|
| UI primitives | `src/components/ui/` | `Button`, `Badge`, `Input`, `Spinner`, `Pill`, `RatingStars` |
| Layout | `src/components/layout/` | `PageShell`, `NavyGridGlowBackground`, `ScrollProgressBar` |
| Section helpers | `src/components/section/` | `SectionTitle`, `SectionGlow`, `SectionFade` |
| Domain — product | `src/components/product/` | `ProductCard`, `CategoryTile` |
| Domain — order | `src/components/order/` | `OrderSummary`, `OrderStatusBadge` |
| Domain — reviews | `src/components/reviews/` | `Testimonial`, `RatingStars` |
| Pages | `src/pages/` | `HomePage`, `ProductPage`, `CheckoutPage` |

### TypeScript

- `strict: true`. No `any`. If a type is genuinely unknown, use `unknown` and narrow it.
- Define API response shapes as interfaces in `src/types/`. Co-locate with their domain (e.g. `src/types/order.ts`).
- Use discriminated unions for UI state that has multiple modes (e.g. `idle | loading | success | error`).
- Prefer named exports. Only use default exports for page components (Vite convention).
- Type all Framer Motion `variants` objects as `Variants` from `framer-motion`.

### Data Fetching

- Use **React Query** (`@tanstack/react-query`) for all server state. Do not store server data in `useState`.
- Query keys follow the pattern `['domain', 'action', ...params]` — e.g. `['products', 'list', { page: 1 }]`.
- Mutations that affect cached data must call `queryClient.invalidateQueries` with the appropriate key on success.
- Show meaningful loading and error states. Never silently fail on the UI.

### Forms

- Use **React Hook Form** for all forms.
- Define Zod schemas for form validation in `src/schemas/`. Import them into both the form component and (if relevant) the API layer.

---

## What Not to Do

**Backend**
- Do not bypass the service layer and call repositories directly from controllers.
- Do not publish Kafka events inside a database transaction.
- Do not use Redis as the source of truth for stock or order state.
- Do not use `@Transactional` on repository methods — annotate at the service level.
- Do not add unbounded database queries — always paginate list endpoints.

**Frontend**
- Do not introduce new colour classes outside the established palette without discussion.
- Do not use white, light-grey, or any non-dark background on a page shell or section.
- Do not re-implement `SectionTitle`, `SectionGlow`, `SectionFade`, `FeatureCard`, `ProductCard`, or any other already-extracted component inline — import and reuse them.
- Do not render `NavyGridGlowBackground` more than once — it belongs in the root layout only.
- Do not write `whileInView` entrance animations without also setting `variants` from `useAnims()` — keep motion consistent.
- Do not animate without checking `useReducedMotion` — all motion must go through the `useAnims()` hook or respect the same guard.
- Do not use `useEffect` to sync server state into local state — use React Query.
- Do not use `any` in TypeScript.
- Do not add `backdrop-blur` to elements that do not also have a semi-transparent background — it has no visible effect and wastes GPU compositing.

---

## Running the Project

```bash
# Backend
./mvnw spring-boot:run

# Frontend
npm install
npm run dev

# Infrastructure (Kafka, Redis, MySQL)
docker compose up -d
```