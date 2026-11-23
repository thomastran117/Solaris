# Architecture Decision Records

An architecture decision record (ADR) captures a significant, hard-to-reverse engineering decision: the context that forced it, the option chosen, the alternatives rejected, and the consequences the team accepts.

ADRs complement the rest of the handbook. The other handbook pages describe **current** behavior; an ADR records **why** the system is (or is becoming) shaped a particular way. When an ADR's decision is implemented, update the affected handbook pages in the same change. Do not describe planned behavior anywhere else in the handbook; speculative ideas without an owner belong in the [candidate roadmap](../roadmap.md).

## When to Write One

Write an ADR before implementation when a change:

- replaces or adds a framework, runtime, datastore, or deployment model;
- changes an authentication, authorization, caching, or rendering boundary;
- sets a convention that many future changes must follow; or
- is expensive to undo once other work depends on it.

Bug fixes, isolated features, and refactors that keep existing boundaries do not need an ADR.

## Numbering and Files

- File name: `NNNN-short-kebab-title.md`, numbered sequentially from `0001`. Never reuse a number.
- One decision per file. A later decision that changes an earlier one gets a new ADR that links back, and the earlier ADR's status becomes `Superseded by NNNN`.
- Accepted ADRs are append-only apart from status updates, link fixes, and an `Implementation log` section that records which pull requests delivered each part.

## Statuses

| Status | Meaning |
|---|---|
| `Proposed` | Under review; not yet agreed. |
| `Accepted` | Agreed; implementation may be pending or in progress. |
| `Implemented` | Every part of the decision has shipped and the handbook reflects it. |
| `Superseded by NNNN` | Replaced by a later ADR. |
| `Rejected` | Considered and declined; kept for history. |

## Template

```markdown
# NNNN. Title

- Status: Proposed
- Date: YYYY-MM-DD
- Tracking: #issue

## Context
What forces the decision? Cite current code paths and constraints.

## Decision
What we will do, stated precisely enough to review an implementation against it.

## Alternatives Considered
Each option and why it was not chosen.

## Consequences
What becomes easier, what becomes harder, and the risks accepted.

## Rollout and Rollback
How the decision ships incrementally and how to back it out.

## Implementation Log
| Part | Pull request | Status |
|---|---|---|
```

## Index

| ADR | Title | Status |
|---|---|---|
| [0001](0001-nextjs-app-router.md) | Migrate the frontend from a Vite SPA to Next.js App Router | Accepted |
