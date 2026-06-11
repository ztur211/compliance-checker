create table projects (
    id          uuid primary key,
    name        text not null,
    created_at  timestamptz not null default now()
);

create table floor_plans (
    id                  uuid primary key,
    project_id          uuid not null references projects(id) on delete cascade,
    name                text not null,
    level               int  not null default 0,
    risk_group          text,
    sprinklered         boolean,
    escape_height_metres double precision,
    geometry_json       jsonb not null default '{"schemaVersion":1,"spaces":[],"doors":[]}'::jsonb,
    schema_version      int  not null default 1,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create index idx_floor_plans_project on floor_plans (project_id);
