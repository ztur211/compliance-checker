create table rule_sets (
    id          uuid primary key,
    name        text not null,
    version     text not null,
    active      boolean not null default false,
    created_at  timestamptz not null default now()
);

create table rules (
    id            uuid primary key,
    rule_set_id   uuid not null references rule_sets(id) on delete cascade,
    citation      text not null,
    title         text,
    parameter     text not null,
    comparator    text not null,
    threshold     double precision not null,
    severity      text not null default 'ERROR',
    risk_groups   text,                 -- comma-separated; empty = all
    status        text not null default 'DRAFT',  -- DRAFT | APPROVED | REJECTED
    source_quote  text,
    confidence    double precision,
    created_at    timestamptz not null default now()
);

create index idx_rules_set on rules (rule_set_id);
create index idx_rules_status on rules (status);

create table extraction_runs (
    id            uuid primary key,
    rule_set_id   uuid references rule_sets(id) on delete set null,
    source        text,
    model         text,
    candidate_count int not null default 0,
    created_at    timestamptz not null default now()
);
