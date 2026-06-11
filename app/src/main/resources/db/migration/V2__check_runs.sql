create table check_runs (
    id            uuid primary key,
    floor_plan_id uuid not null references floor_plans(id) on delete cascade,
    status        text not null,
    result_json   jsonb,
    error         text,
    created_at    timestamptz not null default now(),
    finished_at   timestamptz
);
create index idx_check_runs_floor_plan on check_runs (floor_plan_id);
