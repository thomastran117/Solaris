-- Feature 15 — Chargeback & Dispute Automation
--
-- Stripe dispute webhooks (charge.dispute.created / .updated / .closed) open an internal case
-- with pre-populated evidence. Style follows V1__baseline.sql: lowercase snake_case, no enum
-- CHECK constraints (the Java enum is the source of truth), fk_<table>_<column> naming.

    create table dispute_cases (
        amount_cents bigint not null,
        closed_at timestamp(6) with time zone,
        created_at timestamp(6) with time zone not null,
        evidence_deadline timestamp(6) with time zone,
        updated_at timestamp(6) with time zone not null,
        version bigint not null,
        id uuid not null,
        order_id uuid,
        currency varchar(3) not null,
        outcome varchar(20) not null,
        status varchar(20) not null,
        stripe_status varchar(40),
        reason varchar(60),
        stripe_charge_id varchar(100),
        stripe_dispute_id varchar(100) not null,
        stripe_payment_intent_id varchar(255),
        primary key (id),
        constraint uq_dispute_stripe_id unique (stripe_dispute_id)
    );

    create table dispute_evidence (
        created_at timestamp(6) with time zone not null,
        version bigint not null,
        created_by_id uuid,
        dispute_case_id uuid not null,
        id uuid not null,
        evidence_type varchar(30) not null,
        attachment_url varchar(500),
        content TEXT not null,
        primary key (id)
    );

    create index idx_dispute_order
       on dispute_cases (order_id);

    create index idx_dispute_status
       on dispute_cases (status);

    create index idx_dispute_evidence_case
       on dispute_evidence (dispute_case_id);

    alter table if exists dispute_cases
       add constraint fk_dispute_cases_order_id
       foreign key (order_id)
       references orders;

    alter table if exists dispute_evidence
       add constraint fk_dispute_evidence_dispute_case_id
       foreign key (dispute_case_id)
       references dispute_cases;
