# Candidate Roadmap

This page records possible directions for ShopWave. It is not a delivery schedule, commitment, or description of current functionality. Items have no promised order or date and require design review against the code before implementation.

For implemented capabilities, use the [domain guide](domain-guide.md).

## Checkout and Order Experience

- Build a cohesive customer checkout route that joins cart, address, shipping, tax, payment, and review steps already represented across backend services.
- Expose complete customer self-service UI for returns and subscription management.
- Explore gift wrap/messages, charity round-up, driver tipping, delivery instructions, split-address shipping, and safe pre-fulfillment order edits.
- Complete preorder deposit and later balance-capture behavior with explicit failure compensation.

## Catalog and Merchandising

- Add richer product media such as video, 360-degree views, and optional 3D/AR assets.
- Support structured size guides and fit assistance for applicable categories.
- Explore customer-supplied product personalization with immutable order-line snapshots.
- Extend time-boxed merchandising into capacity-aware flash-sale experiences.

## Payments and Global Commerce

- Evaluate multi-currency display, pricing, settlement, refund, and accounting boundaries.
- Explore protection plans or warranties with a dedicated claim lifecycle.
- Add digital-product delivery and license management where fulfillment and abuse controls are well defined.
- Expand affiliate attribution, commission approval, and payout operations.

## Customer Trust and Engagement

- Add explicit privacy export, erasure, and consent-management workflows.
- Extend loyalty from points and tiers into opt-in challenges, badges, and achievements.
- Continue improving notification-channel preferences and resilient delivery.
- Expand chargeback evidence automation while retaining staff review and auditability.

## Fulfillment and Marketplace Scale

- Improve multi-location and multi-recipient fulfillment planning.
- Expand carrier, tracking, return-location, and driver workflows.
- Strengthen marketplace seller tooling around analytics, purchasing, stock movement, and B2B operations.

## Platform Evolution

- Evaluate a dedicated recommendation compute service only when scale and model requirements justify a new deployable system.
- Introduce generated OpenAPI documentation if external API consumers become a primary audience.
- Add production deployment, backup, restore, observability, and release runbooks once the target platform is selected.

## Promoting an Idea

Before an item moves from this page into implementation:

1. Audit existing backend, frontend, schema, tests, and historical planning material.
2. Identify current capabilities that can be reused and stale assumptions that must be discarded.
3. Define user outcomes, authorization, failure behavior, compatibility, and acceptance tests.
4. Decide data ownership and consistency requirements.
5. Create an implementation issue or design document with an explicit owner and priority.

Until that happens, roadmap entries should not be presented as available product features.
